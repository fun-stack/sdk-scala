package funstack.backend

import facade.amazonaws.services.cognitoidentityprovider._
import cats.effect.IO
import scala.scalajs.js

@js.native
trait UserInfoResponse extends js.Object {
  def email: String = js.native
  def email_verified: String = js.native
}

class Auth(cognitoUserPoolId: String) {
  private val cognito = new CognitoIdentityProvider()

  private implicit val cs  = IO.contextShift(scala.concurrent.ExecutionContext.global)

  def getUser(username: String): IO[UserInfoResponse] = IO.fromFuture(
    IO(
    cognito.adminGetUserFuture(AdminGetUserRequest(
      UserPoolId = cognitoUserPoolId,
      Username = username
    ))
  )).map(_.UserAttributes.fold(js.Dictionary.empty[String])(attrs => js.Dictionary(attrs.flatMap(attr => attr.Value.map(value => (attr.Name, value)).toOption).toSeq :_*)).asInstanceOf[UserInfoResponse])
}
