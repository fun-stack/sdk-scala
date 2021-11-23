package funstack.web

object Fun {
  val auth = AppConfig.auth.map(Auth.apply).toOption
  val api  = AppConfig.api.map(new Api(_)).toOption
  val http = AppConfig.http.map(new Http(_, auth)).toOption
}
