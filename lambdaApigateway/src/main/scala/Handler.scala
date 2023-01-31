package funstack.lambda.apigateway

import cats.data.Kleisli
import cats.effect.IO
import net.exoego.facade.aws_lambda._

import scala.concurrent.Future
import scala.scalajs.js

case class AuthInfo(sub: String, groups: Set[String])
case class Request(event: js.Any, context: Context, auth: Option[AuthInfo])

object Handler {
  type FunctionType = js.Function2[js.Any, Context, js.Promise[APIGatewayProxyStructuredResultV2]]

  type FutureFunc[Out]    = Request => Future[Out]
  type FutureKleisli[Out] = Kleisli[Future, Request, Out]
  type IOFunc[Out]        = Request => IO[Out]
  type IOKleisli[Out]     = Kleisli[IO, Request, Out]
}
