package funstack.lambda.apigateway.helper.facades

import scala.scalajs.js
import scala.scalajs.js.annotation.JSName

@js.native
trait APIGatewayAuthorizer extends js.Object {
  def sub: js.UndefOr[String] = js.native
  @JSName("cognito:groups")
  def cognitoGroups: js.UndefOr[js.Array[String]]                                  = js.native
}
