package funstack.web

object Fun {
  val auth = AppConfig.auth.map(Auth.apply)
  val api  = AppConfig.api.map(new Api(_))
  val http = AppConfig.http.map(new Http(_))
}
