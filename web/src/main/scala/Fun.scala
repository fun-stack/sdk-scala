package funstack.web

import cats.effect.IO

object Fun {
  val auth                = AppConfig.auth.map(new Auth[IO](_, AppConfig.website)).toOption
  def wsWithEvents[Event] = AppConfig.ws.map(new Ws[Event](_, auth)).toOption
  val ws                  = wsWithEvents[Nothing]
  val http                = AppConfig.http.map(new Http(_, auth)).toOption
}
