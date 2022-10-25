package funstack.client.node

import funstack.client.core.AppConfig

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport

class Fun private (val config: AppConfig, val authOption: Option[Auth]) extends funstack.client.core.Fun[Auth]
object Fun {
  def apply(config: AppConfig, redirectUrl: String): Fun = {
    val authOption = config.auth.map(new Auth(_, redirectUrl, config.region)).toOption
    new Fun(config, authOption)
  }
}
