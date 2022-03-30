package funstack.web

import funstack.core.CanSerialize
import cats.effect.{unsafe, LiftIO}

class Http(http: HttpAppConfig, auth: Option[Auth]) {
  def transport[T: CanSerialize] = HttpTransport[T](http, auth)

  // TODO Async
  def transportF[T: CanSerialize, F[_]: LiftIO] = HttpTransport[T](http, auth).map(LiftIO[F].liftIO)

  def transportFuture[T: CanSerialize] = HttpTransport[T](http, auth).map(_.unsafeToFuture()(unsafe.IORuntime.global))
}
