package funstack.web.tapir

import funstack.web.Fun.{config, authOption, MissingModuleException}

object Fun {

  val httpOption = config.http.map(new HttpTapir(_, authOption)).toOption

  lazy val http = httpOption.getOrElse(throw MissingModuleException("http"))
}
