package funstack.lambda.http

import net.exoego.facade.aws_lambda._
import funstack.core.StringSerdes
import scala.scalajs.js
import sloth._
import chameleon.{Serializer, Deserializer}
import scala.scalajs.js.JSConverters._
import funstack.lambda.http.core.HandlerFunction
import cats.effect.IO
import cats.data.Kleisli
import scala.concurrent.Future

import scala.concurrent.ExecutionContext.Implicits.global

object Handler {

  case class HttpAuth(sub: String, username: String)
  case class HttpRequest(event: APIGatewayProxyEventV2, context: Context, auth: Option[HttpAuth])

  type FunctionType = HandlerFunction.Type

  type FutureFunc[Out]    = HttpRequest => Future[Out]
  type FutureKleisli[Out] = Kleisli[Future, HttpRequest, Out]
  type IOFunc[Out]        = HttpRequest => IO[Out]
  type IOKleisli[Out]     = Kleisli[IO, HttpRequest, Out]

  def handle[T : Serializer[*, StringSerdes] : Deserializer[*, StringSerdes]](
      router: Router[T, IO],
  ): FunctionType = handleF[T, Unit, IO](router, _.map(Right.apply).unsafeToFuture())

  def handleFuture[T : Serializer[*, StringSerdes] : Deserializer[*, StringSerdes]](
      router: Router[T, Future],
  ): FunctionType = handleF[T, Unit, Future](router, _.map(Right.apply))

  def handleF[T : Serializer[*, StringSerdes] : Deserializer[*, StringSerdes], Failure, F[_]](
      router: Router[T, F],
      execute: F[T] => Future[Either[Failure, T]],
  ): FunctionType = handleFWithContext[T, Failure, F](router, (f, _) => execute(f))

  def handle[T : Serializer[*, StringSerdes] : Deserializer[*, StringSerdes]](
      router: HttpRequest => Router[T, IO],
  ): FunctionType = handleF[T, Unit, IO](router, _.map(Right.apply).unsafeToFuture())

  def handleFuture[T : Serializer[*, StringSerdes] : Deserializer[*, StringSerdes]](
      router: HttpRequest => Router[T, Future],
  ): FunctionType = handleF[T, Unit, Future](router, _.map(Right.apply))

  def handleF[T : Serializer[*, StringSerdes] : Deserializer[*, StringSerdes], Failure, F[_]](
      router: HttpRequest => Router[T, F],
      execute: F[T] => Future[Either[Failure, T]],
  ): FunctionType = handleFCustom[T, Failure, F](router, (f, _) => execute(f))

  def handleFunc[T : Serializer[*, StringSerdes] : Deserializer[*, StringSerdes]](
      router: Router[T, IOFunc],
  ): FunctionType = handleFWithContext[T, Unit, IOFunc](router, (f, ctx) => f(ctx).map(Right.apply).unsafeToFuture())

  def handleKleisli[T : Serializer[*, StringSerdes] : Deserializer[*, StringSerdes]](
      router: Router[T, IOKleisli],
  ): FunctionType = handleFWithContext[T, Unit, IOKleisli](router, (f, ctx) => f(ctx).map(Right.apply).unsafeToFuture())

  def handleFutureKleisli[T : Serializer[*, StringSerdes] : Deserializer[*, StringSerdes]](
      router: Router[T, FutureKleisli],
  ): FunctionType = handleFWithContext[T, Unit, FutureKleisli](router, (f, ctx) => f(ctx).map(Right.apply))

  def handleFutureFunc[T : Serializer[*, StringSerdes] : Deserializer[*, StringSerdes]](
      router: Router[T, FutureFunc],
  ): FunctionType = handleFWithContext[T, Unit, FutureFunc](router, (f, ctx) => f(ctx).map(Right.apply))

  def handleFWithContext[T : Serializer[*, StringSerdes] : Deserializer[*, StringSerdes], Failure, F[_]](
      router: Router[T, F],
      execute: (F[T], HttpRequest) => Future[Either[Failure, T]],
  ): FunctionType = handleFCustom[T, Failure, F](_ => router, execute)

  def handleFCustom[T : Serializer[*, StringSerdes] : Deserializer[*, StringSerdes], Failure, F[_]](
      routerf: HttpRequest => Router[T, F],
      execute: (F[T], HttpRequest) => Future[Either[Failure, T]],
  ): FunctionType = { (event, context) =>
    // println(js.JSON.stringify(event))
    // println(js.JSON.stringify(context))

    val auth = event.requestContext.authorizer.toOption.flatMap { auth =>
      val authDict = auth.asInstanceOf[js.Dictionary[js.Dictionary[String]]]
      for {
        claims <- authDict.get("lambda")
        sub <- claims.get("sub")
        username <- claims.get("username")
      } yield HttpAuth(sub = sub, username = username)
    }

    val request = HttpRequest(event, context, auth)
    val router = routerf(request)

    val path = event.requestContext.http.path.split("/").toList.drop(2)
    val body = event.body.getOrElse("")
    val result = Deserializer[T, StringSerdes].deserialize(StringSerdes(body)) match {
      case Right(payload) => router(Request(path, payload)) match {
        case Right(result) => execute(result, request).map {
          case Right(value) =>
            APIGatewayProxyStructuredResultV2(body = Serializer[T, StringSerdes].serialize(value).value, statusCode = 200)
          case Left(failure) =>
            println(s"Failure: $failure")
            APIGatewayProxyStructuredResultV2(statusCode = 500)
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

    result.recover { case t =>
      println(s"Error handling request: $t")
      APIGatewayProxyStructuredResultV2(statusCode = 500)
    }.toJSPromise
  }
}
