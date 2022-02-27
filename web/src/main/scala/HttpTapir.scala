package funstack.web

import cats.effect.IO
import cats.implicits._
import sttp.client3.impl.cats.FetchCatsBackend
import sttp.tapir.client.sttp.{SttpClientInterpreter, SttpClientOptions}
import sttp.tapir.PublicEndpoint
import sttp.model.Uri
import scala.concurrent.{Future, ExecutionContext}

class HttpTapir(http: HttpAppConfig, auth: Option[Auth[IO]]) {
  private implicit val cs = IO.contextShift(ExecutionContext.global)

  private val backend = for {
    currentUser <- auth.flatTraverse(_.currentUser.headIO)
  } yield FetchCatsBackend[IO](customizeRequest = request => {
    currentUser.foreach { user =>
      request.headers.append("Authorization", s"${user.token.token_type} ${user.token.access_token}")
    }
    request
  })

  private val clientInterpreter = SttpClientInterpreter(SttpClientOptions.default)

  def client[I, E, O](endpoint: PublicEndpoint[I, E, O, Any]): I => IO[Either[E, O]] = i => backend.flatMap { backend =>
    val f = clientInterpreter.toClientThrowDecodeFailures[IO, I, E, O, Any](endpoint, Some(Uri.unsafeParse(http.url)), backend)
    f(i)
  }

  def clientFuture[I, E, O](endpoint: PublicEndpoint[I, E, O, Any]): I => Future[Either[E, O]] = {
    val ioClient = client(endpoint)
    i => ioClient(i).unsafeToFuture()
  }
}

