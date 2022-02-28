package funstack.web

import funstack.core.CanSerialize
import sloth._
import scala.concurrent.Future
import cats.effect.{Async, IO}

import scala.concurrent.ExecutionContext.Implicits.global

class Http(http: HttpAppConfig, auth: Option[Auth[IO]]) {
  private implicit val cs = IO.contextShift(global)

  def transport[T: CanSerialize] = HttpTransport[T](http, auth)

  def transportF[T: CanSerialize, F[_]: Async] = HttpTransport[T](http, auth).map(Async[F].liftIO)

  def transportFuture[T: CanSerialize] = HttpTransport[T](http, auth).map(_.unsafeToFuture())
}
