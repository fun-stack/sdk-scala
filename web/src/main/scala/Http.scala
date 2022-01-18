package funstack.web

import cats.effect.IO
import sttp.client3.impl.cats.FetchCatsBackend
import sttp.tapir.client.sttp.{SttpClientInterpreter, SttpClientOptions}
import sttp.tapir.PublicEndpoint
import sttp.model.Uri
import scala.concurrent.{Future, ExecutionContext}

class Http(http: HttpAppConfig, auth: Option[Auth[IO]]) {
  //TODO: would be better to use that in an IO per request. We could use currentUser.headIO.flatMap(...)
  private var currentToken = Option.empty[String]
  auth.foreach { auth =>
    auth.currentUser.foreach { user =>
      currentToken = user.map(_.token.access_token)
    }
  }

  private implicit val cs = IO.contextShift(ExecutionContext.global)
  private val backend = FetchCatsBackend[IO](customizeRequest = request => {
    currentToken.foreach { token =>
      request.headers.append("Authorization", s"Bearer $token")
    }
    request
  })

  private val clientInterpreter = SttpClientInterpreter(SttpClientOptions.default)

  def client[I, E, O](endpoint: PublicEndpoint[I, E, O, Any]): I => IO[Either[E, O]] =
    clientInterpreter.toClientThrowDecodeFailures[IO, I, E, O, Any](endpoint, Some(Uri.unsafeParse(http.url)), backend)

  def clientFuture[I, E, O](endpoint: PublicEndpoint[I, E, O, Any]): I => Future[Either[E, O]] = {
    val ioClient = client(endpoint)
    i => ioClient(i).unsafeToFuture()
  }
}
