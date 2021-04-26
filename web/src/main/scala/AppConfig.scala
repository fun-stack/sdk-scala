package funstack.web

import scala.scalajs.js
import scala.scalajs.js.annotation.JSGlobal

@js.native
@JSGlobal
object AppConfig extends js.Object {
  def environment: String     = js.native
  def domain: String          = js.native
  def domainAuth: String      = js.native
  def domainWS: String        = js.native
  def clientIdAuth: String    = js.native
  def region: String          = js.native
  def cognitoEndpoint: String = js.native
  def identityPoolId: String  = js.native
}
