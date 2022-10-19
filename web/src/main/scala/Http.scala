package funstack.web

import cats.effect.{LiftIO, unsafe}
import funstack.core.CanSerialize

class Http(http: HttpAppConfig, auth: Option[Auth]) {
  def transport[T: CanSerialize] = HttpTransport[T](http, auth)

  // TODO Async
  def transportF[T: CanSerialize, F[_]: LiftIO] = HttpTransport[T](http, auth).map(LiftIO[F].liftIO)

  def transportFuture[T: CanSerialize] = HttpTransport[T](http, auth).map(_.unsafeToFuture()(unsafe.IORuntime.global))
}
