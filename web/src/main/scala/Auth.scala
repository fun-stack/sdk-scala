package funstack.web

import funstack.web.helper.facades.JwtDecode

import colibri._
import colibri.jsdom.EventObservable
import cats.effect.{unsafe, IO, Sync}
import cats.implicits._

import org.scalajs.dom
import org.scalajs.dom.window.{localStorage, sessionStorage}

import scala.scalajs.js
import scala.scalajs.js.annotation.JSName
import scala.concurrent.Future

@js.native
trait TokenResponse extends js.Object {
  def id_token: String      = js.native
  def refresh_token: String = js.native
  def access_token: String  = js.native
  def expires_in: Double    = js.native
  def token_type: String    = js.native
}
object TokenResponse {
  def apply(
    id_token: String,
    refresh_token: String,
    access_token: String,
    expires_in: Double,
    token_type: String,
  ): TokenResponse = js.Dynamic
    .literal(
      id_token = id_token,
      refresh_token = refresh_token,
      access_token = access_token,
      expires_in = expires_in,
      token_type = token_type,
    )
    .asInstanceOf[TokenResponse]
}

@js.native
trait UserInfoResponse extends js.Object {
  def sub: String             = js.native
  @JSName("cognito:username")
  def username: String        = js.native
  def email: String           = js.native
  def email_verified: Boolean = js.native
}
object UserInfoResponse {
  def apply(
    sub: String,
    username: String,
    email: String,
    email_verified: Boolean,
  ): UserInfoResponse = js.Dynamic
    .literal(
      sub = sub,
      email = email,
      email_verified = email_verified,
      username = username,
    )
    .asInstanceOf[UserInfoResponse]
}

case class User(
  info: UserInfoResponse,
  token: IO[TokenResponse],
)

case class AuthTokenEndpointError(status: Int) extends Exception(s"Error from endpoint: ${status}")

private sealed trait InitialAuth
private object InitialAuth {
  case object None                         extends InitialAuth
  case object Redirect                     extends InitialAuth
  case class Token(get: IO[TokenResponse]) extends InitialAuth
}

class Auth(val auth: AuthAppConfig, website: WebsiteAppConfig) {

  import org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits.global

  private val authTokenInLocalStorage = website.authTokenInLocalStorage.getOrElse(true)

  private val redirectUrl = dom.window.location.origin.getOrElse(website.url)

  private val authRequests = new AuthRequests(auth, redirectUrl)

  val signupUrl = s"${auth.url}/signup?response_type=code&client_id=${auth.clientId}&redirect_uri=${redirectUrl}"
  val loginUrl  = s"${auth.url}/oauth2/authorize?response_type=code&client_id=${auth.clientId}&redirect_uri=${redirectUrl}"
  val logoutUrl = s"${auth.url}/logout?client_id=${auth.clientId}&logout_uri=${redirectUrl}%3Flogout"

  private object StorageKey {
    val refreshToken = "auth.refresh_token"
    val userId       = "auth.userId"
    val redirectFlag = "auth.redirect"
  }

  private def storeTokenInLocalStorage(userId: String, token: String): Unit = {
    if (authTokenInLocalStorage) localStorage.setItem(StorageKey.refreshToken, token)
    else localStorage.setItem(StorageKey.userId, userId)
    sessionStorage.removeItem(StorageKey.redirectFlag)
  }

  private def cleanupLocalStorage(): Unit = {
    localStorage.removeItem(StorageKey.refreshToken)
    localStorage.removeItem(StorageKey.userId)
    sessionStorage.removeItem(StorageKey.redirectFlag)
  }

  private def redirectForReauthentication(check: => Boolean): Boolean = {
    def shouldRedirect = sessionStorage.getItem(StorageKey.redirectFlag) == null

    if (shouldRedirect && check) {
      sessionStorage.setItem(StorageKey.redirectFlag, "")
      // needs to be in a timeout, otherwise messes with the history stack
      dom.window.setTimeout(() => dom.window.location.href = loginUrl, 0)
      true
    }
    else false
  }

  private val askForReauthentication: IO[Boolean] = {
    def askAndRedirect(): Boolean = redirectForReauthentication {
      dom.window.confirm("You have logged in inside another tab. Do you want to reload this tab?")
    }

    // we can only call confirm inside of a user-event in chrome
    if (dom.document.hasFocus()) IO(askAndRedirect())
    else EventObservable[dom.Event](dom.window, "focus").headIO.map(_ => askAndRedirect())
  }

  private def localStorageEvent(key: String) = EventObservable[dom.StorageEvent](dom.window, "storage")
    .filter(ev => ev.key == key && ev.storageArea == localStorage)
    .filter(ev => ev.oldValue != ev.newValue)
    .distinctByOnEquals(_.newValue)

  private val localStorageEventUserId = localStorageEvent(StorageKey.userId).mapEffect {
    case ev if ev.newValue != null => askForReauthentication
    case _                         => IO.pure(false)
  }.collect { case false => None }

  private val localStorageEventRefreshToken = localStorageEvent(StorageKey.refreshToken).mapEffect {
    case ev if ev.newValue != null => authRequests.getTokenFromRefreshToken(ev.newValue).map(Some.apply)
    case _                         => IO.pure(None)
  }

  private val initialAuthentication: InitialAuth = {
    val searchParams = new dom.URLSearchParams(dom.window.location.search)
    val code         = Option(searchParams.get("code"))
    val logout       = Option(searchParams.get("logout"))

    def resetUrl(): Unit = dom.window.history.replaceState(null, "", dom.window.location.origin.get)

    if (logout.isDefined) {
      cleanupLocalStorage()
      resetUrl()
    }

    if (code.isDefined) {
      resetUrl()
    }

    code match {
      case None if authTokenInLocalStorage =>
        Option(localStorage.getItem(StorageKey.refreshToken)) match {
          case None        => InitialAuth.None
          case Some(token) => InitialAuth.Token(authRequests.getTokenFromRefreshToken(token))
        }

      case None =>
        Option(localStorage.getItem(StorageKey.userId)) match {
          case None    => InitialAuth.None
          case Some(_) =>
            val redirecting = redirectForReauthentication(true)
            if (redirecting) InitialAuth.Redirect else InitialAuth.None
        }

      case Some(code) => InitialAuth.Token(authRequests.getTokenFromAuthCode(code))
    }
  }

  private val customAuthentication = Subject.publish[Option[TokenResponse]]()

  val isRedirecting: Boolean = initialAuthentication == InitialAuth.Redirect

  val signup: IO[Unit]             = signupF[IO]
  def signupF[F[_]: Sync]: F[Unit] = Sync[F].delay(dom.window.location.href = signupUrl)

  val login: IO[Unit]             = loginF[IO]
  def loginF[F[_]: Sync]: F[Unit] = Sync[F].delay(dom.window.location.href = loginUrl)

  val logout: IO[Unit]             = logoutF[IO]
  def logoutF[F[_]: Sync]: F[Unit] = Sync[F].delay(dom.window.location.href = logoutUrl)

  def customLogin(tokenResponse: TokenResponse): IO[Unit]             = customLoginF[IO](tokenResponse)
  def customLoginF[F[_]: Sync](tokenResponse: TokenResponse): F[Unit] = customAuthentication.onNextF[F](Some(tokenResponse))

  val customLogout: IO[Unit]             = customLogoutF[IO]
  def customLogoutF[F[_]: Sync]: F[Unit] = customAuthentication.onNextF[F](None)

  val currentUser: Observable[Option[User]] = Observable
    .merge(
      initialAuthentication match {
        case InitialAuth.None            => Observable(None)
        case InitialAuth.Redirect        => Observable.empty
        case InitialAuth.Token(getToken) => Observable.fromEffect(getToken).map(Some.apply)
      },
      if (authTokenInLocalStorage) localStorageEventRefreshToken else localStorageEventUserId,
      customAuthentication,
    )
    .map {
      case None               => None
      case Some(initialToken) =>
        val userInfo = JwtDecode(initialToken.id_token).asInstanceOf[UserInfoResponse]

        storeTokenInLocalStorage(userId = userInfo.sub, token = initialToken.refresh_token)

        var currentToken     = Future.successful(initialToken)
        var currentTokenTime = js.Date.now()
        val tokenGetter      = IO.fromFuture(IO {
          currentToken = currentToken.flatMap { currentToken =>
            val timeDiffSecs = (js.Date.now() - currentTokenTime) / 1000
            val canUse       = timeDiffSecs < currentToken.expires_in * 0.8
            if (canUse) Future.successful(currentToken)
            else {
              currentTokenTime = js.Date.now()
              authRequests
                .getTokenFromRefreshToken(currentToken.refresh_token)
                .map { response =>
                  storeTokenInLocalStorage(userId = userInfo.sub, token = response.refresh_token)
                  response
                }
                .unsafeToFuture()(unsafe.IORuntime.global)
            }
          }
          currentToken
        })

        Some(User(userInfo, tokenGetter))
    }
    .replayLatest
    .hot
    .recover { case t => // TODO: why does the recover need to be behind hot? colibri?
      dom.console.error("Error in user handling: " + t)
      cleanupLocalStorage()
      None
    }
}

private class AuthRequests(auth: AuthAppConfig, redirectUrl: String) {
  import org.scalajs.dom.{Fetch, HttpMethod, RequestInit, Response}

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
