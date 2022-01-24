package funstack.lambda.ws

import net.exoego.facade.aws_lambda._

import scala.scalajs.js
import mycelium.core.message._
import sloth._
import chameleon.{Serializer, Deserializer}
import scala.scalajs.js.JSConverters._
import cats.effect.IO
import cats.data.Kleisli
import scala.concurrent.Future

import scala.concurrent.ExecutionContext.Implicits.global

object Handler {

  type FunctionType = js.Function2[APIGatewayWSEvent, Context, js.Promise[APIGatewayProxyStructuredResultV2]]

  type FutureFunc[Out] = APIGatewayWSRequestContext => Future[Out]
  type IOFunc[Out]     = APIGatewayWSRequestContext => IO[Out]
  type IOKleisli[Out]  = Kleisli[IO, APIGatewayWSRequestContext, Out]

  def handle[T, Event](
      router: Router[T, IO],
  )(implicit
      deserializer: Deserializer[ClientMessage[T], String],
      serializer: Serializer[ServerMessage[T, Event, Nothing], String],
  ): FunctionType = handleF[T, Event, Nothing, IO](router, _.map(Right.apply).unsafeToFuture())

  def handleFuture[T, Event](
      router: Router[T, Future],
  )(implicit
      deserializer: Deserializer[ClientMessage[T], String],
      serializer: Serializer[ServerMessage[T, Event, Nothing], String],
  ): FunctionType = handleF[T, Event, Nothing, Future](router, _.map(Right.apply))

  def handleF[T, Event, Failure, F[_]](
      router: Router[T, F],
      execute: F[T] => Future[Either[Failure, T]],
  )(implicit
      deserializer: Deserializer[ClientMessage[T], String],
      serializer: Serializer[ServerMessage[T, Event, Failure], String],
  ): FunctionType = handleFWithContext[T, Event, Failure, F](router, (f, _) => execute(f))

  def handle[T, Event](
      router: APIGatewayWSRequestContext => Router[T, IO],
  )(implicit
      deserializer: Deserializer[ClientMessage[T], String],
      serializer: Serializer[ServerMessage[T, Event, Nothing], String],
  ): FunctionType = handleF[T, Event, Nothing, IO](router, _.map(Right.apply).unsafeToFuture())

  def handleFuture[T, Event](
      router: APIGatewayWSRequestContext => Router[T, Future],
  )(implicit
      deserializer: Deserializer[ClientMessage[T], String],
      serializer: Serializer[ServerMessage[T, Event, Nothing], String],
  ): FunctionType = handleF[T, Event, Nothing, Future](router, _.map(Right.apply))

  def handleF[T, Event, Failure, F[_]](
      router: APIGatewayWSRequestContext => Router[T, F],
      execute: F[T] => Future[Either[Failure, T]],
  )(implicit
      deserializer: Deserializer[ClientMessage[T], String],
      serializer: Serializer[ServerMessage[T, Event, Failure], String],
  ): FunctionType = handleFCustom[T, Event, Failure, F](router, (f, _) => execute(f))

  def handleWithContext[T, Event](
      router: Router[T, IOFunc],
  )(implicit
      deserializer: Deserializer[ClientMessage[T], String],
      serializer: Serializer[ServerMessage[T, Event, Nothing], String],
  ): FunctionType = handleFWithContext[T, Event, Nothing, IOFunc](router, (f, ctx) => f(ctx).map(Right.apply).unsafeToFuture())

  def handleKleisliWithContext[T, Event](
      router: Router[T, IOKleisli],
  )(implicit
      deserializer: Deserializer[ClientMessage[T], String],
      serializer: Serializer[ServerMessage[T, Event, Nothing], String],
  ): FunctionType = handleFWithContext[T, Event, Nothing, IOKleisli](router, (f, ctx) => f(ctx).map(Right.apply).unsafeToFuture())

  def handleFutureWithContext[T, Event](
      router: Router[T, FutureFunc],
  )(implicit
      deserializer: Deserializer[ClientMessage[T], String],
      serializer: Serializer[ServerMessage[T, Event, Nothing], String],
  ): FunctionType = handleFWithContext[T, Event, Nothing, FutureFunc](router, (f, ctx) => f(ctx).map(Right.apply))

  def handleFWithContext[T, Event, Failure, F[_]](
      router: Router[T, F],
      execute: (F[T], APIGatewayWSRequestContext) => Future[Either[Failure, T]],
  )(implicit
      deserializer: Deserializer[ClientMessage[T], String],
      serializer: Serializer[ServerMessage[T, Event, Failure], String],
  ): FunctionType = handleFCustom[T, Event, Failure, F](_ => router, execute)

  def handleFCustom[T, Event, Failure, F[_]](
      routerf: APIGatewayWSRequestContext => Router[T, F],
      execute: (F[T], APIGatewayWSRequestContext) => Future[Either[Failure, T]],
  )(implicit
      deserializer: Deserializer[ClientMessage[T], String],
      serializer: Serializer[ServerMessage[T, Event, Failure], String],
  ): FunctionType = { (event, context) =>
    println(js.JSON.stringify(event))
    println(js.JSON.stringify(context))

    val router = routerf(event.requestContext)

    val result: js.Promise[ServerMessage[T, Event, Failure]] = Deserializer[ClientMessage[T], String].deserialize(event.body) match {
      case Left(error) => js.Promise.reject(new Exception(s"Deserializer: $error"))
      case Right(Ping) => js.Promise.resolve[Pong.type](Pong)
      case Right(CallRequest(seqNumber, path, payload)) =>
        router(Request(path, payload)).toEither match {
          case Right(result) => execute(result, event.requestContext).toJSPromise.`then`[ServerMessage[T, Event, Failure]](CallResponse(seqNumber, _))
          case Left(error)   => js.Promise.reject(new Exception(error.toString))
        }
    }

    result
      .`then`[String](Serializer[ServerMessage[T, Event, Failure], String].serialize)
      .`then`[APIGatewayProxyStructuredResultV2](
        payload => APIGatewayProxyStructuredResultV2(body = payload, statusCode = 200),
        (((e: Any) => APIGatewayProxyStructuredResultV2(body = e.toString, statusCode = 500)): js.Function1[
          Any,
          APIGatewayProxyStructuredResultV2,
        ]): js.UndefOr[js.Function1[Any, APIGatewayProxyStructuredResultV2]],
      )
      .`then`[APIGatewayProxyStructuredResultV2]({ (result: APIGatewayProxyStructuredResultV2) =>
        println(js.JSON.stringify(result))
        result: APIGatewayProxyStructuredResultV2
      }: js.Function1[APIGatewayProxyStructuredResultV2, APIGatewayProxyStructuredResultV2])
  }
}
