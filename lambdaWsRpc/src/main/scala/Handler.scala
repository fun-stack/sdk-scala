package funstack.lambda.ws.rpc

import cats.effect.{unsafe, IO}
import funstack.core.{CanSerialize, SubscriptionEvent}
import funstack.lambda.apigateway
import funstack.lambda.apigateway.helper.facades._
import funstack.ws.core.{ClientMessageSerdes, ServerMessageSerdes}
import mycelium.core.message._
import net.exoego.facade.aws_lambda._
import sloth._

import scala.concurrent.Future
import scala.scalajs.js.JSConverters._
import scala.util.control.NonFatal

object Handler {
  import apigateway.Handler._
  import apigateway.Request
  import org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits.global

  def handle[T: CanSerialize](
    router: Router[T, IO],
  ): FunctionType = handleF[T, IO](router, _.map(Right.apply).unsafeToFuture()(unsafe.IORuntime.global))

  def handleFuture[T: CanSerialize](
    router: Router[T, Future],
  ): FunctionType = handleF[T, Future](router, _.map(Right.apply))

  def handleF[T: CanSerialize, F[_]](
    router: Router[T, F],
    execute: F[T] => Future[Either[T, T]],
  ): FunctionType = handleFWithContext[T, F](router, (f, _) => execute(f))

  def handle[T: CanSerialize](
    router: Request => Router[T, IO],
  ): FunctionType = handleF[T, IO](router, _.map(Right.apply).unsafeToFuture()(unsafe.IORuntime.global))

  def handleFuture[T: CanSerialize](
    router: Request => Router[T, Future],
  ): FunctionType = handleF[T, Future](router, _.map(Right.apply))

  def handleF[T: CanSerialize, F[_]](
    router: Request => Router[T, F],
    execute: F[T] => Future[Either[T, T]],
  ): FunctionType = handleFCustom[T, F](router, (f, _) => execute(f))

  def handleFunc[T: CanSerialize](
    router: Router[T, IOFunc],
  ): FunctionType = handleFWithContext[T, IOFunc](router, (f, ctx) => f(ctx).map(Right.apply).unsafeToFuture()(unsafe.IORuntime.global))

  def handleKleisli[T: CanSerialize](
    router: Router[T, IOKleisli],
  ): FunctionType = handleFWithContext[T, IOKleisli](router, (f, ctx) => f(ctx).map(Right.apply).unsafeToFuture()(unsafe.IORuntime.global))

  def handleFutureKleisli[T: CanSerialize](
    router: Router[T, FutureKleisli],
  ): FunctionType = handleFWithContext[T, FutureKleisli](router, (f, ctx) => f(ctx).map(Right.apply))

  def handleFutureFunc[T: CanSerialize](
    router: Router[T, FutureFunc],
  ): FunctionType = handleFWithContext[T, FutureFunc](router, (f, ctx) => f(ctx).map(Right.apply))

  def handleFWithContext[T: CanSerialize, F[_]](
    router: Router[T, F],
    execute: (F[T], Request) => Future[Either[T, T]],
  ): FunctionType = handleFCustom[T, F](_ => router, execute)

  def handleFCustom[T: CanSerialize, F[_]](
    routerf: Request => Router[T, F],
    execute: (F[T], Request) => Future[Either[T, T]],
  ): FunctionType = { (eventAny, context) =>
    // println(js.JSON.stringify(event))
    // println(js.JSON.stringify(context))

    val event   = eventAny.asInstanceOf[APIGatewayWsEvent]
    val auth    = event.requestContext.authorizer.toOption.flatMap { claims =>
      for {
        sub   <- claims.sub.toOption
        groups = claims.cognitoGroups
      } yield apigateway.AuthInfo(sub = sub, groups = groups)
    }
    val request = Request(event, context, auth)
    val router  = routerf(request)

    val result = ClientMessageSerdes.deserialize(event.body) match {
      case Left(error) => Future.failed(error)

      case Right(Ping) => Future.successful(Pong)

      case Right(CallRequest(seqNumber, path, payload)) =>
        val result = router(sloth.Request(path, payload)) match {
          case Right(result)       =>
            execute(result, request).map[ServerMessage[T, SubscriptionEvent, T]] {
              case Right(value)  => CallResponse(seqNumber, value)
              case Left(failure) => CallResponseFailure(seqNumber, failure)
            }
          case Left(serverFailure) => Future.failed(serverFailure.toException)
        }

        result.recover { case NonFatal(t) =>
          println(s"Request Error: $t")
          CallResponseException(seqNumber)
        }
    }

    result.map { message =>
      val serialized = ServerMessageSerdes.serialize(message)
      APIGatewayProxyStructuredResultV2(body = serialized, statusCode = 200)
    }.recover { case NonFatal(t) =>
      println(s"Bad Request: $t")
      APIGatewayProxyStructuredResultV2(body = "Bad Request", statusCode = 400)
    }.toJSPromise
  }
}
