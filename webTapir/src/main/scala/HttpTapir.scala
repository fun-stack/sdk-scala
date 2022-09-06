package funstack.web.tapir

import cats.effect.{unsafe, IO}
import cats.implicits._
import funstack.web.{Auth, HttpAppConfig}
import sttp.client3.impl.cats.FetchCatsBackend
import sttp.model.Uri
import sttp.tapir.PublicEndpoint
import sttp.tapir.client.sttp.{SttpClientInterpreter, SttpClientOptions}

import scala.concurrent.Future

class HttpTapir(http: HttpAppConfig, auth: Option[Auth]) {

  private val backend = for {
    currentUser  <- auth.flatTraverse(_.currentUser.headIO)
    currentToken <- currentUser.traverse(_.token)
  } yield FetchCatsBackend[IO](customizeRequest = request => {
    currentToken.foreach { token =>
      request.headers.append("Authorization", s"${token.token_type} ${token.access_token}")
    }
    request
  })

  private val clientInterpreter = SttpClientInterpreter(SttpClientOptions.default)

  def client[I, E, O](endpoint: PublicEndpoint[I, E, O, Any]): I => IO[Either[E, O]] = i =>
    backend.flatMap { backend =>
      val f = clientInterpreter.toClientThrowDecodeFailures[IO, I, E, O, Any](endpoint, Some(Uri.unsafeParse(http.url)), backend)
      f(i)
    }

  def clientFuture[I, E, O](endpoint: PublicEndpoint[I, E, O, Any]): I => Future[Either[E, O]] = {
    val ioClient = client(endpoint)
    i => ioClient(i).unsafeToFuture()(unsafe.IORuntime.global)
  }
}
