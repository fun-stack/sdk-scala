package funstack.web

import cats.effect.IO

object Fun {
  val auth = AppConfig.auth.map(new Auth[IO](_, AppConfig.website)).toOption
  val ws  = AppConfig.ws.map(new Ws(_, auth)).toOption
  val http = AppConfig.http.map(new Http(_, auth)).toOption
}
