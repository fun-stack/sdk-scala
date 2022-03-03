package funstack.lambda.apigateway

import net.exoego.facade.aws_lambda._

import cats.effect.IO
import cats.data.Kleisli

import scala.concurrent.Future
import scala.scalajs.js

case class AuthInfo(sub: String)
case class RequestOf[+T](event: T, context: Context, auth: Option[AuthInfo])

trait Handler[Event] {
  type Request = RequestOf[Event]

  type FunctionType = js.Function2[Event, Context, js.Promise[APIGatewayProxyStructuredResultV2]]

  type FutureFunc[Out]    = RequestOf[Event] => Future[Out]
  type FutureKleisli[Out] = Kleisli[Future, RequestOf[Event], Out]
  type IOFunc[Out]        = RequestOf[Event] => IO[Out]
  type IOKleisli[Out]     = Kleisli[IO, RequestOf[Event], Out]
}

object Handler extends Handler[Any]
