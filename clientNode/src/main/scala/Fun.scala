package funstack.client.node

import funstack.client.core.AppConfig

class Fun private (val config: AppConfig, val authOption: Option[Auth]) extends funstack.client.core.Fun[Auth]
object Fun {
  def apply(config: AppConfig, appName: String = "fun", redirectPort: Int = 51542): Fun = {
    val authOption = config.auth.map(new Auth(_, appName, redirectPort, config.region)).toOption
    new Fun(config, authOption)
  }
}
