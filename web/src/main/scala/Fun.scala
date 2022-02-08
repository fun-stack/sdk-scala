package funstack.web

import cats.effect.IO

object Fun {
  val config              = AppConfig.load()

  val authOption = config.auth.map(new Auth[IO](_, config.website)).toOption
  val httpOption = config.http.map(new Http(_, authOption)).toOption
  val wsOption   = config.ws.map(new Ws(_, authOption)).toOption

  case class MissingModuleException(name: String) extends Exception(s"Missing module: $name")

  lazy val auth = authOption.getOrElse(throw MissingModuleException("auth"))
  lazy val http = httpOption.getOrElse(throw MissingModuleException("http"))
  lazy val ws   = wsOption.getOrElse(throw MissingModuleException("ws"))
}
