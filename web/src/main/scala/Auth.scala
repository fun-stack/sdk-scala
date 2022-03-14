package funstack.web

import funstack.web.helper.facades.JwtDecode

import colibri._
import colibri.jsdom.EventObservable
import cats.effect.{ContextShift, IO, Sync}
import cats.implicits._

import org.scalajs.dom
import org.scalajs.dom.window.localStorage

import scala.scalajs.js
import scala.scalajs.js.annotation.JSName
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
  def sub: String             = js.native
  def email: String           = js.native
  def email_verified: Boolean = js.native
  @JSName("cognito:username")
  def username: String        = js.native
}

case class User(
  info: UserInfoResponse,
  token: IO[TokenResponse],
)

case class AuthTokenEndpointError(status: Int) extends Exception(s"Error from endpoint: ${status}")

class Auth(val auth: AuthAppConfig, website: WebsiteAppConfig) {

  import ExecutionContext.Implicits.global
  private implicit val cs = IO.contextShift(ExecutionContext.global)

  private val authTokenInLocalStorage = website.authTokenInLocalStorage.getOrElse(true)

  private val redirectUrl  = dom.window.location.origin.getOrElse(website.url)
  private val authRequests = new AuthRequests(auth, redirectUrl)

  private object StorageKey {
    val refreshToken      = "auth.refresh_token"
    val authenticatedFlag = "auth.authenticated"
  }

  private def storeTokenInLocalStorage(token: String): Unit =
    if (authTokenInLocalStorage) localStorage.setItem(StorageKey.refreshToken, token)
    else localStorage.setItem(StorageKey.authenticatedFlag, "true")

  private def cleanupLocalStorage(): Unit = {
    localStorage.removeItem(StorageKey.refreshToken)
    localStorage.removeItem(StorageKey.authenticatedFlag)
  }

  private val initialAuthentication: Option[IO[TokenResponse]] = {
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
      case None       => Option(localStorage.getItem(StorageKey.refreshToken)).map(authRequests.getTokenFromRefreshToken(_))
      case Some(code) => Some(authRequests.getTokenFromAuthCode(code))
    }
  }

  private def doRedirectForReauthentication(onlyOnce: Boolean): Boolean = {
    def isAuthenticated = Option(localStorage.getItem(StorageKey.authenticatedFlag)).flatMap(_.toBooleanOption).getOrElse(false)
    if (!authTokenInLocalStorage && initialAuthentication.isEmpty && isAuthenticated) {
      if (onlyOnce) localStorage.removeItem(StorageKey.authenticatedFlag)
      dom.window.location.href = loginUrl
      true
    }
    else false
  }

  private def askForReauthentication(): Unit = {
    val agree = dom.window.confirm("You have logged in inside another tab. Do you want to reload this tab?")
    if (agree) {
      doRedirectForReauthentication(onlyOnce = false)
      ()
    }
  }

  private def localStorageEvent(key: String) = EventObservable[dom.StorageEvent](dom.window, "storage")
    .distinctByOnEquals(_.newValue)
    .filter(ev => ev.key == key && ev.storageArea == localStorage)

  private val localStorageEventAuthenticatedFlag = localStorageEvent(StorageKey.authenticatedFlag).map {
    case ev if ev.newValue == "true" => Some(askForReauthentication())
    case _                           => None
  }.collect { case None => None }

  private val localStorageEventRefreshToken = localStorageEvent(StorageKey.refreshToken).mapAsync {
    case ev if ev.newValue != null => authRequests.getTokenFromRefreshToken(ev.newValue).map(Some.apply)
    case _                         => IO.pure(None)
  }

  val signupUrl                    = s"${auth.url}/signup?response_type=code&client_id=${auth.clientId}&redirect_uri=${redirectUrl}"
  val signup: IO[Unit]             = signupF[IO]
  def signupF[F[_]: Sync]: F[Unit] = Sync[F].delay(dom.window.location.href = signupUrl)

  val loginUrl                    = s"${auth.url}/oauth2/authorize?response_type=code&client_id=${auth.clientId}&redirect_uri=${redirectUrl}"
  val login: IO[Unit]             = loginF[IO]
  def loginF[F[_]: Sync]: F[Unit] = Sync[F].delay(dom.window.location.href = loginUrl)

  val logoutUrl                    = s"${auth.url}/logout?client_id=${auth.clientId}&logout_uri=${redirectUrl}%3Flogout"
  val logout: IO[Unit]             = logoutF[IO]
  def logoutF[F[_]: Sync]: F[Unit] = Sync[F].delay(dom.window.location.href = logoutUrl)

  val redirectForReauthentication              = redirectForReauthenticationF[IO]
  def redirectForReauthenticationF[F[_]: Sync] = Sync[F].delay(doRedirectForReauthentication(onlyOnce = true))

  val currentUser: Observable[Option[User]] =
    Observable
      .merge(
        initialAuthentication.fold[Observable[Option[TokenResponse]]](Observable(None))(authentication => Observable.fromAsync(authentication).map(Some.apply)),
        if (authTokenInLocalStorage) localStorageEventRefreshToken else localStorageEventAuthenticatedFlag,
      )
      .map {
        case None               => None
        case Some(initialToken) =>
          storeTokenInLocalStorage(initialToken.refresh_token)

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
                    if (initialToken.refresh_token != response.refresh_token) storeTokenInLocalStorage(response.refresh_token)
                    response
                  }
                  .unsafeToFuture()
              }
            }
            currentToken
          })

          val userInfo = JwtDecode(initialToken.id_token).asInstanceOf[UserInfoResponse]

          Some(User(userInfo, tokenGetter))
      }
      .replay
      .hot
      .recover { case t => // TODO: why does the recover need to be behind hot? colibri?
        dom.console.error("Error in user handling: " + t)
        cleanupLocalStorage()
        None
      }
}

private class AuthRequests(auth: AuthAppConfig, redirectUrl: String) {
  import org.scalajs.dom.{Fetch, HttpMethod, RequestInit, Response}

  private def handleResponse(response: Response)(implicit cs: ContextShift[IO]): IO[js.Any] =
    if (response.status == 200) IO.fromFuture(IO(response.json().toFuture))
    else IO.raiseError(AuthTokenEndpointError(response.status))

  def getTokenFromAuthCode(authCode: String)(implicit cs: ContextShift[IO]): IO[TokenResponse] =
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

  def getTokenFromRefreshToken(refreshToken: String)(implicit cs: ContextShift[IO]): IO[TokenResponse] = IO
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
