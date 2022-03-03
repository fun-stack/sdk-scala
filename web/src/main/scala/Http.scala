package funstack.web

import funstack.core.CanSerialize
import cats.effect.{Async, IO}

class Http(http: HttpAppConfig, auth: Option[Auth[IO]]) {
  def transport[T: CanSerialize] = HttpTransport[T](http, auth)

  def transportF[T: CanSerialize, F[_]: Async] = HttpTransport[T](http, auth).map(Async[F].liftIO)

  def transportFuture[T: CanSerialize] = HttpTransport[T](http, auth).map(_.unsafeToFuture())
}
