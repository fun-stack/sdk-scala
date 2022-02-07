package funstack.web

import cats.effect.IO

object Fun {
  val config              = AppConfig.load()
  val auth                = config.auth.map(new Auth[IO](_, config.website)).toOption
  val ws                  = config.ws.map(new Ws(_, auth)).toOption
  val http                = config.http.map(new Http(_, auth)).toOption
}

object FunAll {
  case class MissingModuleException(name: String) extends Exception(s"Missing module: $name")

  val config = Fun.config
  val ws = Fun.ws.getOrElse(throw MissingModuleException("ws"))
  val http = Fun.http.getOrElse(throw MissingModuleException("http"))
  val auth = Fun.auth.getOrElse(throw MissingModuleException("auth"))
}
