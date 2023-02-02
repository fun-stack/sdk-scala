package funstack.client.core.auth

import cats.effect.IO

import scala.scalajs.js
import scala.scalajs.js.annotation.JSName

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
  def sub: String                          = js.native
  @JSName("cognito:username")
  def username: String                     = js.native
  @JSName("cognito:groups")
  def groups: js.UndefOr[js.Array[String]] = js.native
  def email: String                        = js.native
  def email_verified: Boolean              = js.native
}
object UserInfoResponse {
  def apply(
    sub: String,
    username: String,
    groups: js.UndefOr[js.Array[String]],
    email: String,
    email_verified: Boolean,
  ): UserInfoResponse = js.Dynamic
    .literal(
      sub = sub,
      email = email,
      email_verified = email_verified,
      username = username,
      groups = groups,
    )
    .asInstanceOf[UserInfoResponse]
}

case class User(
  info: UserInfoResponse,
  token: IO[TokenResponse],
)

case class AuthTokenEndpointError(status: Int) extends Exception(s"Error from auth endpoint: ${status}") {
  override def toString(): String = getMessage
}
