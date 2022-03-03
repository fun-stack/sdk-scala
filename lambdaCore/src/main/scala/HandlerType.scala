package funstack.lambda.core

import net.exoego.facade.aws_lambda._
import funstack.lambda.core.helper.facades._
import scala.concurrent.Future
import cats.effect.IO
import cats.data.Kleisli

import scala.scalajs.js

trait HandlerType[Event] {
  type Request = RequestOf[Event]

  type FunctionType = js.Function2[Event, Context, js.Promise[APIGatewayProxyStructuredResultV2]]

  type FutureFunc[Out]    = RequestOf[Event] => Future[Out]
  type FutureKleisli[Out] = Kleisli[Future, RequestOf[Event], Out]
  type IOFunc[Out]        = RequestOf[Event] => IO[Out]
  type IOKleisli[Out]     = Kleisli[IO, RequestOf[Event], Out]
}

object types extends HandlerType[Any]
