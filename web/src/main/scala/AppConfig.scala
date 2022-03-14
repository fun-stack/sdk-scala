package funstack.web

import scala.scalajs.js

@js.native
trait WebsiteAppConfig extends js.Object {
  def url: String                        = js.native
  def environment: js.Dictionary[String] = js.native
}
object WebsiteAppConfig {
  implicit class Ops(private val self: WebsiteAppConfig) extends AnyVal {
    def environmentTyped[Env <: js.Any] = self.environment.asInstanceOf[Env]
  }
}

@js.native
trait AuthAppConfig extends js.Object {
  def url: String                  = js.native
  def clientId: String             = js.native
  def apiScope: js.UndefOr[String] = js.native
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
trait AppConfig extends js.Object {
  def stage: String                   = js.native
  def website: WebsiteAppConfig       = js.native
  def auth: js.UndefOr[AuthAppConfig] = js.native
  def http: js.UndefOr[HttpAppConfig] = js.native
  def ws: js.UndefOr[WsAppConfig]     = js.native
}
object AppConfig {
  import js.Dynamic.{global => g}

  def load() = g.AppConfig.asInstanceOf[AppConfig]
}
