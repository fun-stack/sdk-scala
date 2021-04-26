package funstack.web

import funstack.core._

import cats.implicits._
import cats.effect.{ContextShift, IO}
import colibri._

import org.scalajs.dom
import org.scalajs.dom.experimental.{BodyInit, Fetch, RequestInit, HttpMethod, Headers, URLSearchParams}
import scala.scalajs.js
import scala.scalajs.js.JSConverters._
import facade.amazonaws.{AWSConfig, AWSCredentials}
import facade.amazonaws.services.sts._
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
    // credentials: AWSCredentials,
)

case class AuthConfig(
    baseUrl: Url,
    redirectUrl: Url,
    cognitoEndpoint: Url,
    identityPoolId: IdentityPoolId,
    clientId: ClientId,
    region: Region,
)

private sealed trait Authentication
private object Authentication {
  case class AuthCode(code: String)      extends Authentication
  case class RefreshToken(token: String) extends Authentication
}

class Auth(val config: AuthConfig) {
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

  private var currentUserVariable: Option[User] = None

  // def signUrl(credentials: AWSCredentials, url: Url): Future[String] = {
  //   credentials
  //     .refreshPromise()
  //     .`then`[String] { _ =>
  //       AWS4
  //         .sign(
  //           new AWS4SignOptions {
  //             val host      = url.value
  //             val path      = s"?X-Amz-Security-Token=${js.URIUtils.encodeURIComponent(credentials.sessionToken)}"
  //             val service   = "execute-api"
  //             val region    = config.region.value
  //             val signQuery = true
  //           },
  //           new AWS4SignParams {
  //             val accessKeyId     = credentials.accessKeyId
  //             val secretAccessKey = credentials.secretAccessKey
  //           },
  //         )
  //         .path
  //     }
  //     .toFuture
  // }

  def login: IO[Unit] = IO {
    val url = s"${config.baseUrl.value}/login?response_type=code&client_id=${config.clientId.value}&redirect_uri=${config.redirectUrl.value}"
    dom.window.location.href = url
  }

  def logout: IO[Unit] = IO {
    localStorage.removeItem(storageKeyRefreshToken)
    val url = s"${config.baseUrl.value}/logout?client_id=${config.clientId.value}&logout_uri=${config.redirectUrl.value}"
    dom.window.location.href = url
  }

  //TODO headIO colibri
  val currentUserHead: IO[Option[User]] = IO.cancelable[Option[User]] { cb =>
    val cancelable = colibri.Cancelable.variable()
    cancelable() = currentUser.foreach { user =>
      cancelable.cancel()
      cb(Right(user))
    }
    IO(cancelable.cancel())
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
            None
          }
      }
      .doOnNext {
        case Some(user) =>
          dom.console.log(user.token)
          dom.console.log(user.info)
        case None =>
          dom.console.log("Nope")
      }
      .doOnNext(currentUserVariable = _)
      .replay
      .hot

  // private def credentials(token: TokenResponse) = CognitoIdentityCredentials(
  //   new CognitoIdentityCredentialsParams {
  //     IdentityPoolId = config.identityPoolId.value
  //     Logins = js.Dynamic.literal(config.cognitoEndpoint.value -> token.id_token)
  //   },
  //   new CognitoIdentityCredentialsClientConfig {
  //     region = config.region.value
  //   },
  // )

  private def getUserInfo(token: TokenResponse): IO[UserInfoResponse] = IO
    .fromFuture(IO {
      val url = s"${config.baseUrl.value}/oauth2/userInfo"
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
        .`then`[js.Promise[js.Any]](_.json())
        .toFuture
    })
    .map(_.asInstanceOf[UserInfoResponse])

  private def getToken(authCode: String): IO[TokenResponse] = IO
    .fromFuture(IO {
      val url = s"${config.baseUrl.value}/oauth2/token"
      Fetch
        .fetch(
          url,
          new RequestInit {
            method = HttpMethod.POST
            body = s"grant_type=authorization_code&client_id=${config.clientId.value}&code=${authCode}&redirect_uri=${config.redirectUrl.value}"
            headers = js.Array(js.Array("Content-Type", "application/x-www-form-urlencoded"))
          },
        )
        .`then`[js.Promise[js.Any]](_.json())
        .toFuture
    })
    .map(_.asInstanceOf[TokenResponse])

  private def refreshToken(refreshToken: String): IO[TokenResponse] = IO
    .fromFuture(IO {
      val url = s"${config.baseUrl.value}/oauth2/token"
      Fetch
        .fetch(
          url,
          new RequestInit {
            method = HttpMethod.POST
            body = s"grant_type=refresh_token&client_id=${config.clientId.value}&refresh_token=${refreshToken}"
            headers = js.Array(js.Array("Content-Type", "application/x-www-form-urlencoded"))
          },
        )
        .`then`[js.Promise[js.Any]](_.json())
        .toFuture
    })
    .map(r => js.Object.assign(js.Dynamic.literal(refresh_token = refreshToken), r).asInstanceOf[TokenResponse])
}
