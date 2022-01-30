package funstack.web

import cats.effect.IO

object Fun {
  val config              = AppConfig.load()
  val auth                = config.auth.map(new Auth[IO](_, config.website)).toOption
  def wsWithEvents[Event] = config.ws.map(new Ws[Event](_, auth)).toOption
  val ws                  = wsWithEvents[Unit]
  val http                = config.http.map(new Http(_, auth)).toOption
}
