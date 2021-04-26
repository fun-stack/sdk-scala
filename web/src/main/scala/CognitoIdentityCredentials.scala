package funstack.web

import scala.scalajs.js
import scala.scalajs.js.annotation._

import facade.amazonaws.{AWSConfig, AWSCredentials}
import facade.amazonaws.services.cognitoidentity.CognitoIdentity

trait CognitoIdentityCredentialsParams extends js.Object {
  var IdentityPoolId: js.UndefOr[String] = js.undefined
  var Logins: js.UndefOr[js.Object]      = js.undefined
  var RoleARN: js.UndefOr[String]        = js.undefined
}
trait CognitoIdentityCredentialsClientConfig extends js.Object {
  var region: js.UndefOr[String] = js.undefined
}

@js.native
@JSImport("aws-sdk/lib/node_loader", "CognitoIdentityCredentials", "AWS.CognitoIdentityCredentials")
class CognitoIdentityCredentials(params: CognitoIdentityCredentialsParams, config: CognitoIdentityCredentialsClientConfig) extends AWSCredentials

object CognitoIdentityCredentials {
  def apply(params: CognitoIdentityCredentialsParams, config: CognitoIdentityCredentialsClientConfig): CognitoIdentityCredentials = {
    val credentials = new CognitoIdentityCredentials(params, config)

    //TODO: this is needed because if cognito field is not set, it tries to initialize the cognito field internally, but that fails with a `is not a constructor error` at runtime.
    val cognitoConfig = js.Object.assign(js.Object(), config)
    cognitoConfig.asInstanceOf[js.Dynamic].params = params
    credentials.asInstanceOf[js.Dynamic].cognito = new CognitoIdentity(cognitoConfig.asInstanceOf[AWSConfig])

    credentials
  }
}
