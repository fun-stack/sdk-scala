package funstack.backend

import cats.effect.IO
import facade.amazonaws.services.cognitoidentityprovider._

import scala.scalajs.js
import scala.scalajs.js.JSConverters._

@js.native
trait UserInfoResponse extends js.Object {
  def sub: String            = js.native
  def email: String          = js.native
  def email_verified: String = js.native
}

trait Auth {
  def authUrl: String
  def getUser(username: String): IO[UserInfoResponse]
  def getUsernameByEmail(email: String): IO[Option[String]]
}

class AuthAws(val authUrl: String, cognitoUserPoolId: String) extends Auth {
  private val cognito = new CognitoIdentityProvider()

  def getUser(username: String): IO[UserInfoResponse] = IO
    .fromFuture(
      IO(
        cognito.adminGetUserFuture(
          AdminGetUserRequest(
            UserPoolId = cognitoUserPoolId,
            Username = username,
          ),
        ),
      ),
    )
    .map(
      _.UserAttributes
        .fold(js.Dictionary.empty[String]) { attrs =>
          attrs.flatMap(attr => attr.Value.map(value => (attr.Name, value)).toOption).toMap.toJSDictionary
        }
        .asInstanceOf[UserInfoResponse],
    )

  def getUsernameByEmail(email: String): IO[Option[String]] = IO
    .fromFuture(
      IO(
        cognito.listUsersFuture(
          ListUsersRequest(
            UserPoolId = cognitoUserPoolId,
            Filter = s"email=${js.JSON.stringify(email)}",
          ),
        ),
      ),
    )
    .map(_.Users.toOption.flatMap(_.headOption.flatMap(_.Username.toOption)))
}

class AuthDev(val authUrl: String, getEmailFromUser: String => String) extends Auth {
  // TODO: we should inline the dev environment here instead of passing it from the outside
  def getUser(username: String): IO[UserInfoResponse] = IO.pure(
    js.Dynamic
      .literal(
        sub = username,
        email = getEmailFromUser(username),
        email_verified = true,
      )
      .asInstanceOf[UserInfoResponse],
  )

  def getUsernameByEmail(email: String): IO[Option[String]] = IO.pure(
    Some(email) collect { case s"$name@localhost" => name },
  )
}
