package funstack.web

import funstack.core.CanSerialize
import sloth.{Request, RequestTransport}
import cats.effect.IO
import cats.implicits._

import scala.scalajs.js
import scala.concurrent.ExecutionContext.Implicits.global

import org.scalajs.dom.{Fetch, HttpMethod, RequestInit}

private object HttpTransport {

  case class HttpResponseError(msg: String) extends Exception(msg)

  private implicit val cs = IO.contextShift(global)

  def apply[T: CanSerialize](http: HttpAppConfig, auth: Option[Auth[IO]]): RequestTransport[T, IO] =
    new RequestTransport[T, IO] {
      private val trimmedUrl = http.url.replaceFirst("/$", "")

      def apply(request: Request[T]): IO[T] = {
        val path        = request.path.mkString("/")
        val url         = s"${trimmedUrl}/_/$path"
        val requestBody = CanSerialize[T].serialize(request.payload)

        for {
          user      <- auth.flatTraverse(_.currentUser.headIO)
          userToken <- user.traverse(_.token)

          requestHeaders = userToken.fold(js.Array[js.Array[String]]()) { token =>
                             js.Array(js.Array("Authorization", s"${token.token_type} ${token.access_token}"))
                           }

          result <- IO.fromFuture(IO {
                      Fetch.fetch(url, new RequestInit { method = HttpMethod.POST; body = requestBody; headers = requestHeaders }).toFuture
                    })

          text <- IO.fromFuture(IO(result.text().toFuture))

          _ <- IO.whenA(result.status != 200)(IO.raiseError(HttpResponseError(s"Status ${result.status}: $text")))

          deserialized <- IO.fromEither(CanSerialize[T].deserialize(text))
        } yield deserialized
      }
    }
}
