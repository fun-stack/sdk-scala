package funstack.lambda.http

import net.exoego.facade.aws_lambda._
import funstack.lambda.core.{HandlerRequest, AuthInfo}
import funstack.core.StringSerdes
import scala.scalajs.js
import sloth._
import chameleon.{Serializer, Deserializer}
import scala.scalajs.js.JSConverters._
import funstack.lambda.core.HandlerFunction
import cats.effect.IO
import cats.data.Kleisli
import scala.concurrent.Future

import scala.concurrent.ExecutionContext.Implicits.global

object Handler {

  type HttpRequest = HandlerRequest[APIGatewayProxyEventV2]

  type FunctionType = HandlerFunction.Type[APIGatewayProxyEventV2]

  type FutureFunc[Out]    = HttpRequest => Future[Out]
  type FutureKleisli[Out] = Kleisli[Future, HttpRequest, Out]
  type IOFunc[Out]        = HttpRequest => IO[Out]
  type IOKleisli[Out]     = Kleisli[IO, HttpRequest, Out]

  def handle[T : Serializer[*, StringSerdes] : Deserializer[*, StringSerdes]](
      router: Router[T, IO],
  ): FunctionType = handleF[T, IO](router, _.unsafeToFuture())

  def handleFuture[T : Serializer[*, StringSerdes] : Deserializer[*, StringSerdes]](
      router: Router[T, Future],
  ): FunctionType = handleF[T, Future](router, identity)

  def handleF[T : Serializer[*, StringSerdes] : Deserializer[*, StringSerdes], F[_]](
      router: Router[T, F],
      execute: F[T] => Future[T],
  ): FunctionType = handleFWithContext[T, F](router, (f, _) => execute(f))

  def handle[T : Serializer[*, StringSerdes] : Deserializer[*, StringSerdes]](
      router: HttpRequest => Router[T, IO],
  ): FunctionType = handleF[T, IO](router, _.unsafeToFuture())

  def handleFuture[T : Serializer[*, StringSerdes] : Deserializer[*, StringSerdes]](
      router: HttpRequest => Router[T, Future],
  ): FunctionType = handleF[T, Future](router, identity)

  def handleF[T : Serializer[*, StringSerdes] : Deserializer[*, StringSerdes], F[_]](
      router: HttpRequest => Router[T, F],
      execute: F[T] => Future[T],
  ): FunctionType = handleFCustom[T, F](router, (f, _) => execute(f))

  def handleFunc[T : Serializer[*, StringSerdes] : Deserializer[*, StringSerdes]](
      router: Router[T, IOFunc],
  ): FunctionType = handleFWithContext[T, IOFunc](router, (f, ctx) => f(ctx).unsafeToFuture())

  def handleKleisli[T : Serializer[*, StringSerdes] : Deserializer[*, StringSerdes]](
      router: Router[T, IOKleisli],
  ): FunctionType = handleFWithContext[T, IOKleisli](router, (f, ctx) => f(ctx).unsafeToFuture())

  def handleFutureKleisli[T : Serializer[*, StringSerdes] : Deserializer[*, StringSerdes]](
      router: Router[T, FutureKleisli],
  ): FunctionType = handleFWithContext[T, FutureKleisli](router, (f, ctx) => f(ctx))

  def handleFutureFunc[T : Serializer[*, StringSerdes] : Deserializer[*, StringSerdes]](
      router: Router[T, FutureFunc],
  ): FunctionType = handleFWithContext[T, FutureFunc](router, (f, ctx) => f(ctx))

  def handleFWithContext[T : Serializer[*, StringSerdes] : Deserializer[*, StringSerdes], F[_]](
      router: Router[T, F],
      execute: (F[T], HttpRequest) => Future[T],
  ): FunctionType = handleFCustom[T, F](_ => router, execute)

  def handleFCustom[T : Serializer[*, StringSerdes] : Deserializer[*, StringSerdes], F[_]](
      routerf: HttpRequest => Router[T, F],
      execute: (F[T], HttpRequest) => Future[T],
  ): FunctionType = { (event, context) =>
    // println(js.JSON.stringify(event))
    // println(js.JSON.stringify(context))

    val auth = event.requestContext.authorizer.toOption.flatMap { auth =>
      val authDict = auth.asInstanceOf[js.Dictionary[js.Dictionary[String]]]
      for {
        claims <- authDict.get("lambda")
        sub <- claims.get("sub")
        username <- claims.get("username")
      } yield AuthInfo(sub = sub, username = username)
    }

    val request = HandlerRequest(event, context, auth)
    val router = routerf(request)

    val path = event.requestContext.http.path.split("/").toList.drop(2)
    val body = event.body.getOrElse("")
    println(path)
    println(event.body)
    val result = router.getFunction(path) match {
      case Some(function) => Deserializer[T, StringSerdes].deserialize(StringSerdes(body)) match {
        case Right(payload) => function(payload) match {
          case Right(result) => execute(result, request).map { value =>
            APIGatewayProxyStructuredResultV2(body = Serializer[T, StringSerdes].serialize(value).value, statusCode = 200)
          }
          case Left(ServerFailure.PathNotFound(p))   =>
            println(s"Path not found: $p")
            Future.successful(APIGatewayProxyStructuredResultV2(statusCode = 404))
          case Left(ServerFailure.HandlerError(e))   =>
            println(s"Handler error: $e")
            Future.successful(APIGatewayProxyStructuredResultV2(statusCode = 500))
          case Left(ServerFailure.DeserializerError(e))   =>
            println(s"Deserializer error: $e")
            Future.successful(APIGatewayProxyStructuredResultV2(statusCode = 400))
        }
        case Left(e) =>
          println(s"Unexpected payload: $e")
          Future.successful(APIGatewayProxyStructuredResultV2(statusCode = 400))
      }
      case None =>
        println(s"Path not found: $path")
        Future.successful(APIGatewayProxyStructuredResultV2(statusCode = 404))
    }

    result.recover { case t =>
      println(s"Error handling request: $t")
      APIGatewayProxyStructuredResultV2(statusCode = 500)
    }.toJSPromise
  }
}
