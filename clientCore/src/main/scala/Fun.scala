package funstack.client.core

import funstack.client.core.auth.Auth
import funstack.client.core.tapir.HttpTapir
import funstack.core.MissingModuleException

trait Fun[AUTH <: Auth] {
  def config: AppConfig
  def authOption: Option[AUTH]

  val httpApiOption = config.http.map(new HttpTapir(_, authOption)).toOption
  val httpRpcOption = config.http.map(new Http(_, authOption)).toOption
  val wsRpcOption   = config.ws.map(new Ws(_, authOption)).toOption

  lazy val auth    = authOption.getOrElse(throw MissingModuleException("auth"))
  lazy val httpApi = httpApiOption.getOrElse(throw MissingModuleException("http-api"))
  lazy val httpRpc = httpRpcOption.getOrElse(throw MissingModuleException("http-rpc"))
  lazy val wsRpc   = wsRpcOption.getOrElse(throw MissingModuleException("ws-rpc"))
}
