package funstack.lambda.core

import scala.concurrent.Future
import cats.effect.IO
import cats.data.Kleisli

object HandlerType {
  type FutureFunc[Out]    = HandlerRequest[_] => Future[Out]
  type FutureKleisli[Out] = Kleisli[Future, HandlerRequest[_], Out]
  type IOFunc[Out]        = HandlerRequest[_] => IO[Out]
  type IOKleisli[Out]     = Kleisli[IO, HandlerRequest[_], Out]
}
