package funstack.lambda.apigateway.helper.facades

import scala.scalajs.js
import scala.scalajs.js.annotation.JSName

@js.native
trait APIGatewayAuthorizer extends js.Object {
  def sub: js.UndefOr[String]                 = js.native
  @JSName("cognito:groups")
  def cognitoGroupsString: js.UndefOr[String] = js.native
}
object APIGatewayAuthorizer {
  implicit class Ops(private val self: APIGatewayAuthorizer) extends AnyVal {
    def cognitoGroups: Set[String] = {
      val parsedArray = self.cognitoGroupsString.map(str => js.JSON.parse(str).asInstanceOf[js.Array[String]])

      parsedArray.toOption.toSet.flatten
    }
  }
}
