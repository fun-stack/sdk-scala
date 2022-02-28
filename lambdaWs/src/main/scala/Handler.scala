package funstack.lambda.ws

import net.exoego.facade.aws_lambda._

import funstack.lambda.core.{HandlerFunction, HandlerRequest, AuthInfo}
import funstack.core.StringSerdes
import scala.scalajs.js
import mycelium.core.message._
import sloth._
import chameleon.{Serializer, Deserializer}
import scala.scalajs.js.JSConverters._
import cats.effect.IO
import cats.data.Kleisli
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

  def handle[T](
      router: Router[T, IO],
  )(implicit
      deserializer: Deserializer[ClientMessage[T], StringSerdes],
      serializer: Serializer[ServerMessage[T, Unit, Unit], StringSerdes],
  ): FunctionType = handleF[T, Unit, IO](router, _.map(Right.apply).unsafeToFuture())

  def handleFuture[T](
      router: Router[T, Future],
  )(implicit
      deserializer: Deserializer[ClientMessage[T], StringSerdes],
      serializer: Serializer[ServerMessage[T, Unit, Unit], StringSerdes],
  ): FunctionType = handleF[T, Unit, Future](router, _.map(Right.apply))

  def handleF[T, Failure, F[_]](
      router: Router[T, F],
      execute: F[T] => Future[Either[Failure, T]],
  )(implicit
      deserializer: Deserializer[ClientMessage[T], StringSerdes],
      serializer: Serializer[ServerMessage[T, Unit, Failure], StringSerdes],
  ): FunctionType = handleFWithContext[T, Failure, F](router, (f, _) => execute(f))

  def handle[T](
      router: WsRequest => Router[T, IO],
  )(implicit
      deserializer: Deserializer[ClientMessage[T], StringSerdes],
      serializer: Serializer[ServerMessage[T, Unit, Unit], StringSerdes],
  ): FunctionType = handleF[T, Unit, IO](router, _.map(Right.apply).unsafeToFuture())

  def handleFuture[T](
      router: WsRequest => Router[T, Future],
  )(implicit
      deserializer: Deserializer[ClientMessage[T], StringSerdes],
      serializer: Serializer[ServerMessage[T, Unit, Unit], StringSerdes],
  ): FunctionType = handleF[T, Unit, Future](router, _.map(Right.apply))

  def handleF[T, Failure, F[_]](
      router: WsRequest => Router[T, F],
      execute: F[T] => Future[Either[Failure, T]],
  )(implicit
      deserializer: Deserializer[ClientMessage[T], StringSerdes],
      serializer: Serializer[ServerMessage[T, Unit, Failure], StringSerdes],
  ): FunctionType = handleFCustom[T, Failure, F](router, (f, _) => execute(f))

  def handleFunc[T](
      router: Router[T, IOFunc],
  )(implicit
      deserializer: Deserializer[ClientMessage[T], StringSerdes],
      serializer: Serializer[ServerMessage[T, Unit, Unit], StringSerdes],
  ): FunctionType = handleFWithContext[T, Unit, IOFunc](router, (f, ctx) => f(ctx).map(Right.apply).unsafeToFuture())

  def handleKleisli[T](
      router: Router[T, IOKleisli],
  )(implicit
      deserializer: Deserializer[ClientMessage[T], StringSerdes],
      serializer: Serializer[ServerMessage[T, Unit, Unit], StringSerdes],
  ): FunctionType = handleFWithContext[T, Unit, IOKleisli](router, (f, ctx) => f(ctx).map(Right.apply).unsafeToFuture())

  def handleFutureKleisli[T](
      router: Router[T, FutureKleisli],
  )(implicit
      deserializer: Deserializer[ClientMessage[T], StringSerdes],
      serializer: Serializer[ServerMessage[T, Unit, Unit], StringSerdes],
  ): FunctionType = handleFWithContext[T, Unit, FutureKleisli](router, (f, ctx) => f(ctx).map(Right.apply))

  def handleFutureFunc[T](
      router: Router[T, FutureFunc],
  )(implicit
      deserializer: Deserializer[ClientMessage[T], StringSerdes],
      serializer: Serializer[ServerMessage[T, Unit, Unit], StringSerdes],
  ): FunctionType = handleFWithContext[T, Unit, FutureFunc](router, (f, ctx) => f(ctx).map(Right.apply))

  def handleFWithContext[T, Failure, F[_]](
      router: Router[T, F],
      execute: (F[T], WsRequest) => Future[Either[Failure, T]],
  )(implicit
      deserializer: Deserializer[ClientMessage[T], StringSerdes],
      serializer: Serializer[ServerMessage[T, Unit, Failure], StringSerdes],
  ): FunctionType = handleFCustom[T, Failure, F](_ => router, execute)

  def handleFCustom[T, Failure, F[_]](
      routerf: WsRequest => Router[T, F],
      execute: (F[T], WsRequest) => Future[Either[Failure, T]],
  )(implicit
      deserializer: Deserializer[ClientMessage[T], StringSerdes],
      serializer: Serializer[ServerMessage[T, Unit, Failure], StringSerdes],
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

    val result = Deserializer[ClientMessage[T], StringSerdes].deserialize(StringSerdes(event.body)) match {
      case Left(error) => Future.failed(error)

      case Right(Ping) => Future.successful(Pong)

      case Right(CallRequest(seqNumber, path, payload)) =>
        val result = router(Request(path, payload)) match {
          case Right(result) => execute(result, request).map[ServerMessage[T, Unit, Failure]] {
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
        val serialized = Serializer[ServerMessage[T, Unit, Failure], StringSerdes].serialize(message)
        APIGatewayProxyStructuredResultV2(body = serialized.value, statusCode = 200)
      }.recover { case NonFatal(t) =>
        println(s"Bad Request: $t")
        APIGatewayProxyStructuredResultV2(body = "Bad Request", statusCode = 400)
      }.toJSPromise
  }
}
