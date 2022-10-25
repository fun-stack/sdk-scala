package funstack.client.web

import funstack.client.core.AppConfig

object Fun extends funstack.client.core.Fun[Auth] {
  lazy val config     = AppConfig.load()
  lazy val authOption = config.auth.map(new Auth(_, config.website)).toOption
}
