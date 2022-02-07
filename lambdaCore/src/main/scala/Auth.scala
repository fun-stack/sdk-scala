package funstack.lambda.core

import facade.amazonaws.services.cognitoidentityprovider._
import cats.effect.IO
import scala.scalajs.js

object Auth {
  private val cognito = new CognitoIdentityProvider()

  private implicit val cs  = IO.contextShift(scala.concurrent.ExecutionContext.global)

  def getUser(username: String): IO[js.Dictionary[String]] = IO.fromFuture(
    IO(
    cognito.adminGetUserFuture(AdminGetUserRequest(
      UserPoolId = js.Dynamic.global.process.env.FUN_AUTH_COGNITO_POOL_ID.asInstanceOf[String], //TODO config? move to backend?
      Username = username
    ))
  )).map(_.UserAttributes.fold(js.Dictionary.empty[String])(attrs => js.Dictionary(attrs.flatMap(attr => attr.Value.map(value => (attr.Name, value)).toOption).toSeq :_*)))
}
