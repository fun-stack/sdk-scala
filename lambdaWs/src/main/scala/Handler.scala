package funstack.lambda.ws

import funstack.ws.core.{ClientMessageSerdes, ServerMessageSerdes}
import funstack.lambda.core.{HandlerFunction, HandlerRequest, AuthInfo}
import funstack.core.{SubscriptionEvent, CanSerialize}

import net.exoego.facade.aws_lambda._
import mycelium.core.message._
import sloth._

import cats.effect.IO
import cats.data.Kleisli

import scala.scalajs.js.JSConverters._
import scala.concurrent.Future
import scala.util.control.NonFatal

import scala.concurrent.ExecutionContext.Implicits.global

object Handler {

  type WsRequest = HandlerRequest[APIGatewayWSEvent]

  type FunctionType = HandlerFunction.Type[APIGatewayWSEvent]

  type FutureFunc[Out]    = WsRequest => Future[Out]
  type FutureKleisli[Out] = Kleisli[Future, WsRequest, Out]
  type IOFunc[Out]        = WsRequest => IO[Out]
  type IOKleisli[Out]     = Kleisli[IO, WsRequest, Out]

  def handle[T: CanSerialize](
      router: Router[T, IO],
  ): FunctionType = handleF[T, IO](router, _.map(Right.apply).unsafeToFuture())

  def handleFuture[T: CanSerialize](
      router: Router[T, Future],
  ): FunctionType = handleF[T, Future](router, _.map(Right.apply))

  def handleF[T: CanSerialize, F[_]](
      router: Router[T, F],
      execute: F[T] => Future[Either[T, T]],
  ): FunctionType = handleFWithContext[T, F](router, (f, _) => execute(f))

  def handle[T: CanSerialize](
      router: WsRequest => Router[T, IO],
  ): FunctionType = handleF[T, IO](router, _.map(Right.apply).unsafeToFuture())

  def handleFuture[T: CanSerialize](
      router: WsRequest => Router[T, Future],
  ): FunctionType = handleF[T, Future](router, _.map(Right.apply))

  def handleF[T: CanSerialize, F[_]](
      router: WsRequest => Router[T, F],
      execute: F[T] => Future[Either[T, T]],
  ): FunctionType = handleFCustom[T, F](router, (f, _) => execute(f))

  def handleFunc[T: CanSerialize](
      router: Router[T, IOFunc],
  ): FunctionType = handleFWithContext[T, IOFunc](router, (f, ctx) => f(ctx).map(Right.apply).unsafeToFuture())

  def handleKleisli[T: CanSerialize](
      router: Router[T, IOKleisli],
  ): FunctionType = handleFWithContext[T, IOKleisli](router, (f, ctx) => f(ctx).map(Right.apply).unsafeToFuture())

  def handleFutureKleisli[T: CanSerialize](
      router: Router[T, FutureKleisli],
  ): FunctionType = handleFWithContext[T, FutureKleisli](router, (f, ctx) => f(ctx).map(Right.apply))

  def handleFutureFunc[T: CanSerialize](
      router: Router[T, FutureFunc],
  ): FunctionType = handleFWithContext[T, FutureFunc](router, (f, ctx) => f(ctx).map(Right.apply))

  def handleFWithContext[T: CanSerialize, F[_]](
      router: Router[T, F],
      execute: (F[T], WsRequest) => Future[Either[T, T]],
  ): FunctionType = handleFCustom[T, F](_ => router, execute)

  def handleFCustom[T: CanSerialize, F[_]](
      routerf: WsRequest => Router[T, F],
      execute: (F[T], WsRequest) => Future[Either[T, T]],
  ): FunctionType = { (event, context) =>
    // println(js.JSON.stringify(event))
    // println(js.JSON.stringify(context))

    val auth = event.requestContext.authorizer.toOption.flatMap { claims =>
      for {
        sub <- claims.get("sub")
        username <- claims.get("username")
      } yield AuthInfo(sub = sub, username = username)
    }
    val request = HandlerRequest(event, context, auth)
    val router = routerf(request)

    val result = ClientMessageSerdes.deserialize(event.body) match {
      case Left(error) => Future.failed(error)

      case Right(Ping) => Future.successful(Pong)

      case Right(CallRequest(seqNumber, path, payload)) =>
        val result = router(Request(path, payload)) match {
          case Right(result) => execute(result, request).map[ServerMessage[T, SubscriptionEvent, T]] {
            case Right(value) => CallResponse(seqNumber, value)
            case Left(failure) => CallResponseFailure(seqNumber, failure)
          }
          case Left(serverFailure)   => Future.failed(serverFailure.toException)
        }

        result.recover { case NonFatal(t) =>
          println(s"Request Error: $t")
          CallResponseException(seqNumber)
        }
    }

    result
      .map { message =>
        val serialized = ServerMessageSerdes.serialize(message)
        APIGatewayProxyStructuredResultV2(body = serialized, statusCode = 200)
      }.recover { case NonFatal(t) =>
        println(s"Bad Request: $t")
        APIGatewayProxyStructuredResultV2(body = "Bad Request", statusCode = 400)
      }.toJSPromise
  }
}
