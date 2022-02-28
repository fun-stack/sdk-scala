package funstack.web

import funstack.core.StringSerdes
import sloth.{Request, RequestTransport}
import cats.effect.IO
import cats.implicits._

import scala.scalajs.js
import scala.concurrent.ExecutionContext.Implicits.global

import org.scalajs.dom.{ Fetch, RequestInit, HttpMethod }

private object HttpTransport {

  case class HttpResponseError(msg: String) extends Exception(msg)

  private implicit val cs = IO.contextShift(global)

  def apply(http: HttpAppConfig, auth: Option[Auth[IO]]): RequestTransport[StringSerdes, IO] =
    new RequestTransport[StringSerdes, IO] {
      def apply(request: Request[StringSerdes]): IO[StringSerdes] = {
        val path = request.path.mkString("/")
        val url = s"${http.url}/_/$path"
        val requestBody = request.payload.value

        for {
          user <- auth.flatTraverse(_.currentUser.headIO)

          requestHeaders = user.fold(js.Array[js.Array[String]]()) { user =>
            js.Array(js.Array("Authorization", s"${user.token.token_type} ${user.token.access_token}"))
          }

          result <- IO.fromFuture(IO {
            Fetch.fetch(url, new RequestInit { method = HttpMethod.POST; body = requestBody; headers = requestHeaders }).toFuture
          })

          text <- IO.fromFuture(IO(result.text().toFuture))

          _ <- if (result.status == 200) IO.pure(()) else IO.raiseError(HttpResponseError(s"Status ${result.status}: $text"))
        } yield StringSerdes(text)
      }
    }
}

