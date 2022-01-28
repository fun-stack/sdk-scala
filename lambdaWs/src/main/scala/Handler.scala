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

  type FutureFunc[Out]    = APIGatewayWSRequestContext => Future[Out]
  type FutureKleisli[Out] = Kleisli[Future, APIGatewayWSRequestContext, Out]
  type IOFunc[Out]        = APIGatewayWSRequestContext => IO[Out]
  type IOKleisli[Out]     = Kleisli[IO, APIGatewayWSRequestContext, Out]

  def handle[T](
      router: Router[T, IO],
  )(implicit
      deserializer: Deserializer[ClientMessage[T], String],
      serializer: Serializer[ServerMessage[T, String, String], String],
  ): FunctionType = handleF[T, String, IO](router, _.map(Right.apply).unsafeToFuture())

  def handleFuture[T](
      router: Router[T, Future],
  )(implicit
      deserializer: Deserializer[ClientMessage[T], String],
      serializer: Serializer[ServerMessage[T, String, String], String],
  ): FunctionType = handleF[T, String, Future](router, _.map(Right.apply))

  def handleF[T, Failure, F[_]](
      router: Router[T, F],
      execute: F[T] => Future[Either[Failure, T]],
  )(implicit
      deserializer: Deserializer[ClientMessage[T], String],
      serializer: Serializer[ServerMessage[T, String, Failure], String],
  ): FunctionType = handleFWithContext[T, Failure, F](router, (f, _) => execute(f))

  def handle[T](
      router: APIGatewayWSRequestContext => Router[T, IO],
  )(implicit
      deserializer: Deserializer[ClientMessage[T], String],
      serializer: Serializer[ServerMessage[T, String, String], String],
  ): FunctionType = handleF[T, String, IO](router, _.map(Right.apply).unsafeToFuture())

  def handleFuture[T](
      router: APIGatewayWSRequestContext => Router[T, Future],
  )(implicit
      deserializer: Deserializer[ClientMessage[T], String],
      serializer: Serializer[ServerMessage[T, String, String], String],
  ): FunctionType = handleF[T, String, Future](router, _.map(Right.apply))

  def handleF[T, Failure, F[_]](
      router: APIGatewayWSRequestContext => Router[T, F],
      execute: F[T] => Future[Either[Failure, T]],
  )(implicit
      deserializer: Deserializer[ClientMessage[T], String],
      serializer: Serializer[ServerMessage[T, String, Failure], String],
  ): FunctionType = handleFCustom[T, Failure, F](router, (f, _) => execute(f))

  def handleFunc[T](
      router: Router[T, IOFunc],
  )(implicit
      deserializer: Deserializer[ClientMessage[T], String],
      serializer: Serializer[ServerMessage[T, String, String], String],
  ): FunctionType = handleFWithContext[T, String, IOFunc](router, (f, ctx) => f(ctx).map(Right.apply).unsafeToFuture())

  def handleKleisli[T](
      router: Router[T, IOKleisli],
  )(implicit
      deserializer: Deserializer[ClientMessage[T], String],
      serializer: Serializer[ServerMessage[T, String, String], String],
  ): FunctionType = handleFWithContext[T, String, IOKleisli](router, (f, ctx) => f(ctx).map(Right.apply).unsafeToFuture())

  def handleFutureKleisli[T](
      router: Router[T, FutureKleisli],
  )(implicit
      deserializer: Deserializer[ClientMessage[T], String],
      serializer: Serializer[ServerMessage[T, String, String], String],
  ): FunctionType = handleFWithContext[T, String, FutureKleisli](router, (f, ctx) => f(ctx).map(Right.apply))

  def handleFutureFunc[T](
      router: Router[T, FutureFunc],
  )(implicit
      deserializer: Deserializer[ClientMessage[T], String],
      serializer: Serializer[ServerMessage[T, String, String], String],
  ): FunctionType = handleFWithContext[T, String, FutureFunc](router, (f, ctx) => f(ctx).map(Right.apply))

  def handleFWithContext[T, Failure, F[_]](
      router: Router[T, F],
      execute: (F[T], APIGatewayWSRequestContext) => Future[Either[Failure, T]],
  )(implicit
      deserializer: Deserializer[ClientMessage[T], String],
      serializer: Serializer[ServerMessage[T, String, Failure], String],
  ): FunctionType = handleFCustom[T, Failure, F](_ => router, execute)

  def handleFCustom[T, Failure, F[_]](
      routerf: APIGatewayWSRequestContext => Router[T, F],
      execute: (F[T], APIGatewayWSRequestContext) => Future[Either[Failure, T]],
  )(implicit
      deserializer: Deserializer[ClientMessage[T], String],
      serializer: Serializer[ServerMessage[T, String, Failure], String],
  ): FunctionType = { (event, context) =>
    println(js.JSON.stringify(event))
    println(js.JSON.stringify(context))

    val router = routerf(event.requestContext)

    val result: js.Promise[ServerMessage[T, String, Failure]] = Deserializer[ClientMessage[T], String].deserialize(event.body) match {
      case Left(error) => js.Promise.reject(new Exception(s"Deserializer: $error"))
      case Right(Ping) => js.Promise.resolve[Pong.type](Pong)
      case Right(CallRequest(seqNumber, path, payload)) =>
        router(Request(path, payload)).toEither match {
          case Right(result) => execute(result, event.requestContext).toJSPromise.`then`[ServerMessage[T, String, Failure]](CallResponse(seqNumber, _))
          case Left(error)   => js.Promise.reject(new Exception(error.toString))
        }
    }

    result
      .`then`[String](Serializer[ServerMessage[T, String, Failure], String].serialize)
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
