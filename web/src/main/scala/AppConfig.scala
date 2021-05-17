package funstack.web

import scala.scalajs.js
import scala.scalajs.js.annotation.JSGlobal

@js.native
trait WebsiteAppConfig extends js.Object {
  def domain: String = js.native
}

@js.native
trait AuthAppConfig extends js.Object {
  def domain: String          = js.native
  def clientIdAuth: String    = js.native
  def cognitoEndpoint: String = js.native
  def identityPoolId: String  = js.native
}

@js.native
trait ApiAppConfig extends js.Object {
  def domain: String                = js.native
  def allowUnauthenticated: Boolean = js.native
}

@js.native
@JSGlobal
object AppConfig extends js.Object {
  def environment: String             = js.native
  def region: String                  = js.native
  def website: WebsiteAppConfig       = js.native
  def auth: js.UndefOr[AuthAppConfig] = js.native
  def api: js.UndefOr[ApiAppConfig]   = js.native
}
