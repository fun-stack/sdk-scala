package funstack.web

import cats.effect.IO

object Fun {
  val auth = AppConfig.auth.map(new Auth[IO](_, AppConfig.website)).toOption
  val api  = AppConfig.api.map(new Api(_)).toOption
  val http = AppConfig.http.map(new Http(_, auth)).toOption
}
