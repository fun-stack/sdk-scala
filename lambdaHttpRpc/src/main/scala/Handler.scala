package funstack.lambda.http.rpc

import cats.effect.{unsafe, IO}
import funstack.core.{CanSerialize, HttpResponseError}
import funstack.lambda.apigateway
import funstack.lambda.apigateway.helper.facades.APIGatewayAuthorizer
import net.exoego.facade.aws_lambda._
import sloth._

import scala.concurrent.Future
import scala.scalajs.js
import scala.scalajs.js.JSConverters._

object Handler {
  import apigateway.Handler._
  import apigateway.Request
  import org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits.global

  def handle[T: CanSerialize](
    router: Router[T, IO],
  ): FunctionType = handleF[T, IO](router, _.unsafeToFuture()(unsafe.IORuntime.global))

  def handleFuture[T: CanSerialize](
    router: Router[T, Future],
  ): FunctionType = handleF[T, Future](router, identity)

  def handleF[T: CanSerialize, F[_]](
    router: Router[T, F],
    execute: F[T] => Future[T],
  ): FunctionType = handleFWithContext[T, F](router, (f, _) => execute(f))

  def handle[T: CanSerialize](
    router: Request => Router[T, IO],
  ): FunctionType = handleF[T, IO](router, _.unsafeToFuture()(unsafe.IORuntime.global))

  def handleFuture[T: CanSerialize](
    router: Request => Router[T, Future],
  ): FunctionType = handleF[T, Future](router, identity)

  def handleF[T: CanSerialize, F[_]](
    router: Request => Router[T, F],
    execute: F[T] => Future[T],
  ): FunctionType = handleFCustom[T, F](router, (f, _) => execute(f))

  def handleFunc[T: CanSerialize](
    router: Router[T, IOFunc],
  ): FunctionType = handleFWithContext[T, IOFunc](router, (f, ctx) => f(ctx).unsafeToFuture()(unsafe.IORuntime.global))

  def handleKleisli[T: CanSerialize](
    router: Router[T, IOKleisli],
  ): FunctionType = handleFWithContext[T, IOKleisli](router, (f, ctx) => f(ctx).unsafeToFuture()(unsafe.IORuntime.global))

  def handleFutureKleisli[T: CanSerialize](
    router: Router[T, FutureKleisli],
  ): FunctionType = handleFWithContext[T, FutureKleisli](router, (f, ctx) => f(ctx))

  def handleFutureFunc[T: CanSerialize](
    router: Router[T, FutureFunc],
  ): FunctionType = handleFWithContext[T, FutureFunc](router, (f, ctx) => f(ctx))

  def handleFWithContext[T: CanSerialize, F[_]](
    router: Router[T, F],
    execute: (F[T], Request) => Future[T],
  ): FunctionType = handleFCustom[T, F](_ => router, execute)

  def handleFCustom[T: CanSerialize, F[_]](
    routerf: Request => Router[T, F],
    execute: (F[T], Request) => Future[T],
  ): FunctionType = { (eventAny, context) =>
    // println(js.JSON.stringify(event))
    // println(js.JSON.stringify(context))

    val event = eventAny.asInstanceOf[APIGatewayProxyEventV2]
    val auth  = event.requestContext.authorizer.toOption.flatMap { auth =>
      val authDict = auth.asInstanceOf[js.Dictionary[APIGatewayAuthorizer]]
      for {
        claims <- authDict.get("lambda")
        sub    <- claims.sub.toOption
        groups  = claims.cognitoGroups
      } yield apigateway.AuthInfo(sub = sub, groups = groups)
    }

    val request = Request(event, context, auth)
    val router  = routerf(request)

    val fullPath = event.requestContext.http.path.split("/").toList.drop(2)
    val body     = event.body.getOrElse("")
    val result   = fullPath match {
      case "_" :: path =>
        CanSerialize[T].deserialize(body) match {
          case Right(payload) =>
            router(sloth.Request(path, payload)) match {
              case Right(result)                                                            =>
                execute(result, request).map { value =>
                  APIGatewayProxyStructuredResultV2(body = CanSerialize[T].serialize(value), statusCode = 200)
                }
              case Left(ServerFailure.PathNotFound(p))                                      =>
                println(s"Path not found: $p")
                Future.successful(APIGatewayProxyStructuredResultV2(statusCode = 404))
              case Left(ServerFailure.HandlerError(HttpResponseError(message, statusCode))) =>
                println(s"Handler http error (statusCode: $statusCode): $message")
                Future.successful(APIGatewayProxyStructuredResultV2(statusCode = statusCode, body = message))
              case Left(ServerFailure.HandlerError(e))                                      =>
                println(s"Handler error: $e")
                Future.successful(APIGatewayProxyStructuredResultV2(statusCode = 500))
              case Left(ServerFailure.DeserializerError(e))                                 =>
                println(s"Deserializer error: $e")
                Future.successful(APIGatewayProxyStructuredResultV2(statusCode = 400))
            }
          case Left(e)        =>
            println(s"Unexpected payload: $e")
            Future.successful(APIGatewayProxyStructuredResultV2(statusCode = 400))
        }

      case _ =>
        println(s"Expected path starting with '/_/', got: $fullPath")
        Future.successful(APIGatewayProxyStructuredResultV2(statusCode = 404))
    }

    result.recover { case t =>
      println(s"Error handling request: $t")
      APIGatewayProxyStructuredResultV2(statusCode = 500)
    }.toJSPromise
  }
}
