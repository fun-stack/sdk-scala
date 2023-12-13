package funstack.client.core

import cats.effect.IO
import cats.implicits._
import funstack.client.core.auth.Auth
import funstack.core.{CanSerialize, HttpResponseError}
import org.scalajs.dom.{Fetch, HttpMethod, RequestInit}
import sloth.{Request, RequestTransport}

import scala.scalajs.js

private object HttpTransport {

  def apply[T: CanSerialize](http: HttpAppConfig, auth: Option[Auth]): RequestTransport[T, IO] =
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

          result <- IO.fromThenable(IO {
                      Fetch.fetch(url, new RequestInit { method = HttpMethod.POST; body = requestBody; headers = requestHeaders })
                    })

          text <- IO.fromThenable(IO(result.text()))

          _ <- IO.whenA(result.status != 200)(IO.raiseError(HttpResponseError(text, result.status)))

          deserialized <- IO.fromEither(CanSerialize[T].deserialize(text))
        } yield deserialized
      }
    }
}
