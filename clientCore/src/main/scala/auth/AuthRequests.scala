package funstack.client.core.auth

import cats.effect.IO
import funstack.client.core.AuthAppConfig

import scala.scalajs.js

class AuthRequests(auth: AuthAppConfig, redirectUrl: String) {
  import org.scalajs.dom.{Fetch, HttpMethod, RequestInit, Response}

  val signupUrl = s"${auth.url}/signup?response_type=code&client_id=${auth.clientId}&redirect_uri=${redirectUrl}"
  val loginUrl  = s"${auth.url}/oauth2/authorize?response_type=code&client_id=${auth.clientId}&redirect_uri=${redirectUrl}"
  val logoutUrl = s"${auth.url}/logout?client_id=${auth.clientId}&logout_uri=${redirectUrl}%3Flogout"

  private def handleResponse(response: Response): IO[js.Any] =
    if (response.status == 200) IO.fromFuture(IO(response.json().toFuture))
    else IO.raiseError(AuthTokenEndpointError(response.status))

  def getTokenFromAuthCode(authCode: String): IO[TokenResponse] =
    IO
      .fromFuture(IO {
        Fetch
          .fetch(
            s"${auth.url}/oauth2/token",
            new RequestInit {
              method = HttpMethod.POST
              body = s"grant_type=authorization_code&client_id=${auth.clientId}&code=${authCode}&redirect_uri=${redirectUrl}"
              headers = js.Array(js.Array("Content-Type", "application/x-www-form-urlencoded"))
            },
          )
          .toFuture
      })
      .flatMap(handleResponse)
      .flatMap(r => IO(r.asInstanceOf[TokenResponse]))

  def getTokenFromRefreshToken(refreshToken: String): IO[TokenResponse] = IO
    .fromFuture(IO {
      Fetch
        .fetch(
          s"${auth.url}/oauth2/token",
          new RequestInit {
            method = HttpMethod.POST
            body = s"grant_type=refresh_token&client_id=${auth.clientId}&refresh_token=${refreshToken}"
            headers = js.Array(js.Array("Content-Type", "application/x-www-form-urlencoded"))
          },
        )
        .toFuture
    })
    .flatMap(handleResponse)
    .flatMap(r => IO(js.Object.assign(js.Dynamic.literal(refresh_token = refreshToken), r.asInstanceOf[js.Object]).asInstanceOf[TokenResponse]))
}
