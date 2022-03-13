package funstack.web

import colibri._
import cats.effect.{Async, IO, Sync}
import cats.effect.concurrent.Ref
import funstack.web.helper.facades.JwtDecode

import cats.effect.IO
import cats.implicits._

import org.scalajs.dom
import org.scalajs.dom.{Fetch, HttpMethod, RequestInit, Response, URLSearchParams}
import org.scalajs.dom.window.localStorage

import scala.scalajs.js
import scala.scalajs.js.annotation.JSName
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

@js.native
trait TokenResponse extends js.Object {
  def id_token: String      = js.native
  def refresh_token: String = js.native
  def access_token: String  = js.native
  def expires_in: Double    = js.native
  def token_type: String    = js.native
}

@js.native
trait UserInfoResponse extends js.Object {
  def sub: String                          = js.native
  def email: String                        = js.native
  def email_verified: Boolean              = js.native
  @JSName("cognito:username")
  def username: String                     = js.native
  @JSName("cognito:groups")
  def groups: js.UndefOr[js.Array[String]] = js.native
}

case class User(
  info: UserInfoResponse,
  token: IO[TokenResponse],
)

case class AuthTokenEndpointError(status: Int) extends Exception(s"Error from endpoint: ${status}")

class Auth[F[_]: Async](val auth: AuthAppConfig, website: WebsiteAppConfig) {

  import ExecutionContext.Implicits.global
  private implicit val cs = IO.contextShift(ExecutionContext.global)

  private val storageKeyRefreshToken = "auth.refresh_token"

  private val initialAuthentication: Option[IO[TokenResponse]] = {
    val searchParams = new URLSearchParams(dom.window.location.search)
    val code         = Option(searchParams.get("code"))
    val logout       = Option(searchParams.get("logout"))

    if (logout.isDefined || code.isDefined) {
      localStorage.removeItem(storageKeyRefreshToken)
      dom.window.history.replaceState(null, "", dom.window.location.origin.get)
    }

    code match {
      case None       => Option(localStorage.getItem(storageKeyRefreshToken)).map(refreshAccessToken(_))
      case Some(code) => Some(getToken(code))
    }
  }

  private val redirectUrl = dom.window.location.origin.getOrElse(website.url)

  val signupUrl       = s"${auth.url}/signup?response_type=code&client_id=${auth.clientId}&redirect_uri=${redirectUrl}"
  val signup: F[Unit] = Sync[F].delay(dom.window.location.href = signupUrl)

  val loginUrl       = s"${auth.url}/login?response_type=code&client_id=${auth.clientId}&redirect_uri=${redirectUrl}"
  val login: F[Unit] = Sync[F].delay(dom.window.location.href = loginUrl)

  val logoutUrl       = s"${auth.url}/logout?client_id=${auth.clientId}&logout_uri=${redirectUrl}%3Flogout"
  val logout: F[Unit] = Sync[F].delay(dom.window.location.href = logoutUrl)

  val currentUser: Observable[Option[User]] =
    initialAuthentication
      .fold[Observable[Option[User]]](Observable(None)) { authentication =>
        Observable
          .fromAsync(authentication) // TODO: localstorage events across tabs
          .map[Option[User]] { initialToken =>
            println("INITIAL TOKEN " + initialToken)
            localStorage.setItem(storageKeyRefreshToken, initialToken.refresh_token)
            var currentToken     = Future.successful(initialToken)
            var currentTokenTime = js.Date.now()
            val tokenGetter      = IO.fromFuture(IO {
              currentToken = currentToken.flatMap { currentToken =>
                val timeDiffSecs = (js.Date.now() - currentTokenTime) / 1000
                val canUse       = timeDiffSecs < currentToken.expires_in * 0.8
                if (canUse) Future.successful(currentToken)
                else {
                  currentTokenTime = js.Date.now()
                  refreshAccessToken(currentToken.refresh_token).flatTap { response =>
                    IO(localStorage.setItem(storageKeyRefreshToken, response.refresh_token))
                  }.unsafeToFuture()
                }
              }
              currentToken
            })

            Some(User(getUserInfo(initialToken), tokenGetter))
          }
      }
      .replay
      .hot
      .recover { case t => // TODO: why does the recover need to be behind hot? colibri?
        dom.console.error("Error in user handling: " + t)
        localStorage.removeItem(storageKeyRefreshToken)
        None
      }

  private def handleResponse(response: Response): IO[js.Any] =
    if (response.status == 200) IO.fromFuture(IO(response.json().toFuture))
    else IO.raiseError(AuthTokenEndpointError(response.status))

  private def getUserInfo(token: TokenResponse): UserInfoResponse = {
    val decoded = JwtDecode(token.id_token)
    js.Dynamic.global.console.log("DECODED", decoded)
    decoded.asInstanceOf[UserInfoResponse]
  }

  private def getToken(authCode: String): IO[TokenResponse] =
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

  private def refreshAccessToken(refreshToken: String): IO[TokenResponse] = IO
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
