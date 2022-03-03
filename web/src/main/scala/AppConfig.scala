package funstack.web

import scala.scalajs.js

@js.native
trait WebsiteAppConfigTyped[T] extends js.Object {
  def url: String    = js.native
  def environment: T = js.native
}

@js.native
trait AuthAppConfig extends js.Object {
  def url: String      = js.native
  def clientId: String = js.native
}

@js.native
trait WsAppConfig extends js.Object {
  def url: String                   = js.native
  def allowUnauthenticated: Boolean = js.native
}

@js.native
trait HttpAppConfig extends js.Object {
  def url: String = js.native
}

@js.native
trait AppConfigTyped[T] extends js.Object {
  def stage: String                     = js.native
  def website: WebsiteAppConfigTyped[T] = js.native
  def auth: js.UndefOr[AuthAppConfig]   = js.native
  def http: js.UndefOr[HttpAppConfig]   = js.native
  def ws: js.UndefOr[WsAppConfig]       = js.native
}
object AppConfig {
  import js.Dynamic.{global => g}

  def load()         = loadTyped[js.Dictionary[String]]()
  def loadTyped[T]() = g.AppConfig.asInstanceOf[AppConfigTyped[T]]
}
