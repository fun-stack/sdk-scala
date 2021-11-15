package funstack.web

import cats.effect.IO
import sttp.client3.impl.cats.FetchCatsBackend
import sttp.tapir.client.sttp.{SttpClientInterpreter, SttpClientOptions}
import sttp.tapir.PublicEndpoint
import sttp.model.Uri
import scala.concurrent.ExecutionContext

class Http(http: HttpAppConfig) {
  private var currentToken = Option.empty[String]
  Fun.auth.foreach { auth =>
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
    clientInterpreter.toClientThrowDecodeFailures[IO, I, E, O, Any](endpoint, Some(Uri.unsafeApply("https", http.domain)), backend)
}
