package funstack.web

import cats.implicits._
import colibri._
import cats.effect.{Async, Sync, IO}

import org.scalajs.dom
import org.scalajs.dom.{Fetch, RequestInit, HttpMethod, URLSearchParams, Response}
import scala.scalajs.js
import org.scalajs.dom.window.localStorage

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext

@js.native
trait TokenResponse extends js.Object {
  def access_token: String  = js.native
  def id_token: String      = js.native
  def refresh_token: String = js.native
  def expires_in: Double    = js.native
  def token_type: String    = js.native
}

@js.native
trait UserInfoResponse extends js.Object {
  def sub: String             = js.native
  def username: String        = js.native
  def email: String           = js.native
  def email_verified: Boolean = js.native
}

case class User(
    info: UserInfoResponse,
    token: TokenResponse,
)

private sealed trait Authentication
private object Authentication {
  case class AuthCode(code: String)      extends Authentication
  case class RefreshToken(token: String) extends Authentication
}

class Auth[F[_]: Async](val auth: AuthAppConfig, website: WebsiteAppConfig) {
  private val redirectUrl = dom.window.location.origin.getOrElse(website.url)

  private implicit val cs = IO.contextShift(ExecutionContext.global)

  private val storageKeyRefreshToken = "auth.refresh_token"

  private val authentication: Option[Authentication] = {
    val code = new URLSearchParams(dom.window.location.search).get("code")
    if (code == null) {
      val refreshToken = localStorage.getItem(storageKeyRefreshToken)
      if (refreshToken == null) None
      else Some(Authentication.RefreshToken(refreshToken))
    } else {
      localStorage.removeItem(storageKeyRefreshToken)
      dom.window.history.replaceState(null, "", dom.window.location.origin.get)
      Some(Authentication.AuthCode(code))
    }
  }

  def signup: F[Unit] = Sync[F].delay {
    val url = s"${auth.url}/signup?response_type=code&client_id=${auth.clientId}&redirect_uri=${redirectUrl}"
    dom.window.location.href = url
  }

  def login: F[Unit] = Sync[F].delay {
    val url = s"${auth.url}/login?response_type=code&client_id=${auth.clientId}&redirect_uri=${redirectUrl}"
    dom.window.location.href = url
  }

  def logout: F[Unit] = Sync[F].delay {
    localStorage.removeItem(storageKeyRefreshToken)
    val url = s"${auth.url}/logout?client_id=${auth.clientId}&logout_uri=${redirectUrl}"
    dom.window.location.href = url
  }

  val currentUser: Observable[Option[User]] =
    authentication
      .fold[Observable[Option[User]]](Observable(None)) { authentication =>
        Observable(authentication)
          .mapAsync {
            case Authentication.AuthCode(code)      => getToken(code)
            case Authentication.RefreshToken(token) => refreshToken(token)
          }
          .mapAsync(token => getUserInfo(token).map(info => User(info, token)))
          .switchMap { user =>
            localStorage.setItem(storageKeyRefreshToken, user.token.refresh_token)
            Observable
              .interval((user.token.expires_in * 0.8).seconds) // TODO: dynamic per token
              .drop(1)
              .mapAsync(_ => refreshToken(user.token.refresh_token))
              .map(token => Option(user.copy(token = token)))
              .prepend(Option(user))
          }
          .recover { case t =>
            dom.console.error("Error in user handling: " + t)
            localStorage.removeItem(storageKeyRefreshToken)
            None
          }
      }
      .replay
      .hot

  private def handleResponse(response: Response): IO[js.Any] =
    if (response.status == 200) IO.fromFuture(IO(response.json().toFuture))
    else IO.raiseError(new Exception(s"Got Error Response: ${response.status}"))

  private def getUserInfo(token: TokenResponse): IO[UserInfoResponse] = IO
    .fromFuture(IO {
      val url = s"${auth.url}/oauth2/userInfo"
      Fetch
        .fetch(
          url,
          new RequestInit {
            method = HttpMethod.GET
            headers = js.Array(
              js.Array("Authorization", s"${token.token_type} ${token.access_token}"),
              js.Array("Content-Type", "application/json;charset=utf8"),
            )
          },
        )
        .toFuture
    })
    .flatMap(handleResponse)
    .flatMap(r => IO(r.asInstanceOf[UserInfoResponse]))

  private def getToken(authCode: String): IO[TokenResponse] = IO
    .fromFuture(IO {
      val url = s"${auth.url}/oauth2/token"
      Fetch
        .fetch(
          url,
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

  private def refreshToken(refreshToken: String): IO[TokenResponse] = IO
    .fromFuture(IO {
      val url = s"${auth.url}/oauth2/token"
      Fetch
        .fetch(
          url,
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
