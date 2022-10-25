package funstack.client.node

import cats.implicits._
import cats.effect.IO
import cats.effect.unsafe
import colibri._
import facade.amazonaws.AWSConfig
import funstack.client.core.AuthAppConfig
import funstack.client.core.helper.facades.JwtDecode
import funstack.client.core.auth._
import facade.amazonaws.services.cognitoidentityprovider._
import funstack.client.node.helper.facades.{FS, OS, Path}

import scala.concurrent.Future
import scala.scalajs.js

private sealed trait AuthMethod
private object AuthMethod {
  case object Empty                                          extends AuthMethod
  case object Web                                            extends AuthMethod
  case class Credentials(username: String, password: String) extends AuthMethod
  case class Token(tokenCredentials: TokenCredentials)       extends AuthMethod
}

private case class TokenCredentials(time: Double, token: TokenResponse)

class Auth(val auth: AuthAppConfig, redirectUrl: String, awsRegion: String) extends funstack.client.core.auth.Auth {

  private object paths {

    val configDir       = Path.join(OS.homedir(), ".priceloop")
    val credentialsFile = Path.join(configDir, "credentials")
  }

  private val cognitoIdentityServiceProvider = new CognitoIdentityProvider(AWSConfig(region = awsRegion))

  private val authRequests = new AuthRequests(auth, redirectUrl)

  private val initialAuth = readFromCredentialsFile() match {
    case Some(tokenCredentials) => AuthMethod.Token(tokenCredentials)
    case None                   => AuthMethod.Empty
  }

  private val authSubject = Subject.publish[AuthMethod]()

  def loginWeb: IO[Unit]                                             = authSubject.onNextIO(AuthMethod.Web)
  def loginCredentials(username: String, password: String): IO[Unit] = authSubject.onNextIO(AuthMethod.Credentials(username, password))
  def logout: IO[Unit]                                               = authSubject.onNextIO(AuthMethod.Empty)

  val currentUser: Observable[Option[User]] =
    authSubject
      .prepend(initialAuth)
      .switchMap {
        case AuthMethod.Empty                           => Observable.pure(None)
        case AuthMethod.Token(tokenCredentials)         => Observable.pure(Some(tokenCredentials))
        case AuthMethod.Web                             => ???
        case AuthMethod.Credentials(username, password) =>
          Observable
            .fromEffect(loginToCognito(username, password))
            .map(_.AuthenticationResult.toOption.map { result =>
              TokenCredentials(
                js.Date.now(),
                TokenResponse(
                  id_token = result.IdToken.get,
                  refresh_token = result.RefreshToken.get,
                  access_token = result.AccessToken.get,
                  expires_in = result.ExpiresIn.get.toDouble,
                  token_type = result.TokenType.get,
                ),
              )
            })
      }
      .map {
        case None               =>
          deleteCredentialsFile()
          None
        case Some(initialToken) =>
          val userInfo = JwtDecode(initialToken.token.id_token).asInstanceOf[UserInfoResponse]

          writeToCredentialsFile(initialToken)

          var currentToken = Future.successful(initialToken)
          val tokenGetter  = IO
            .fromFuture(IO {
              currentToken = currentToken.flatMap { current =>
                val timeDiffSecs = (js.Date.now() - current.time) / 1000
                val canUse       = timeDiffSecs < current.token.expires_in * 0.8
                if (canUse) Future.successful(current)
                else {
                  authRequests
                    .getTokenFromRefreshToken(current.token.refresh_token)
                    .map { response =>
                      val tokenCredentials = TokenCredentials(js.Date.now(), response)
                      writeToCredentialsFile(tokenCredentials)
                      tokenCredentials
                    }
                    .unsafeToFuture()(unsafe.IORuntime.global)
                }
              }(unsafe.IORuntime.global.compute)
              currentToken
            })
            .map(_.token)

          Some(User(userInfo, tokenGetter))
      }
      .replayLatest
      .hot
      .recover { case t => // TODO: why does the recover need to be behind hot? colibri?
        println("Error in user handling: " + t)
        None
      }

  private def loginToCognito(username: String, password: String): IO[InitiateAuthResponse] = IO
    .fromFuture(
      IO {
        cognitoIdentityServiceProvider.initiateAuthFuture(
          InitiateAuthRequest(
            AuthFlowType.USER_PASSWORD_AUTH,
            auth.clientId,
            AuthParameters = js.Dictionary(
              ("USERNAME", username),
              ("PASSWORD", password),
            ),
          ),
        )
      },
    )

  private def readFromCredentialsFile(): Option[TokenCredentials] =
    if (FS.existsSync(paths.credentialsFile)) {
      val result = Either.catchNonFatal(FS.readFileSync(paths.credentialsFile)).flatMap { buffer =>
        val json = buffer.toString("utf-8")
        Either.catchNonFatal(js.JSON.parse(json))
      }

      result match {
        case Right(value) => Some(TokenCredentials(value.time.asInstanceOf[Double], value.token.asInstanceOf[TokenResponse]))
        case Left(err)    =>
          println(s"Unexpected error reading credentials file: $err")
          None
      }
    }
    else None

  private def writeToCredentialsFile(tokenCredentials: TokenCredentials): Unit = {
    if (!FS.existsSync(paths.configDir)) FS.mkdirSync(paths.configDir)
    FS.writeFileSync(paths.credentialsFile, js.JSON.stringify(js.Dynamic.literal(time = tokenCredentials.time, token = tokenCredentials.token)))
  }

  private def deleteCredentialsFile(): Unit =
    if (FS.existsSync(paths.credentialsFile)) {
      FS.rmSync(paths.credentialsFile)
    }
}
