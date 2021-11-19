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
}

@js.native
trait ApiAppConfig extends js.Object {
  def domain: String                = js.native
  def allowUnauthenticated: Boolean = js.native
}

@js.native
trait HttpAppConfig extends js.Object {
  def domain: String = js.native
}

@js.native
@JSGlobal
object AppConfig extends js.Object {
  def stage: String                      = js.native
  def region: String                     = js.native
  def website: WebsiteAppConfig          = js.native
  def auth: js.UndefOr[AuthAppConfig]    = js.native
  def http: js.UndefOr[HttpAppConfig]    = js.native
  def api: js.UndefOr[ApiAppConfig]      = js.native
  def environment: js.Dictionary[String] = js.native
}
