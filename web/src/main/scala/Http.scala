package funstack.web

import funstack.core.StringSerdes
import sloth._
import scala.concurrent.Future
import cats.effect.{Async, IO}

import scala.concurrent.ExecutionContext.Implicits.global

class Http(http: HttpAppConfig, auth: Option[Auth[IO]]) {
  private implicit val cs = IO.contextShift(global)

  // def client = clientF[IO]
  def client = Client[StringSerdes, IO](HttpTransport(http, auth))

  def clientF[F[_]: Async] = Client[StringSerdes, F](HttpTransport(http, auth).map(Async[F].liftIO))

  def clientFuture = Client[StringSerdes, Future](HttpTransport(http, auth).map(_.unsafeToFuture()))

  val tapir = new HttpTapir(http, auth)
}
