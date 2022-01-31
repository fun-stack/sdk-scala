package funstack.backend

import scala.scalajs.js

case class ConfigTyped[T](
  apiGatewayEndpoint: Option[String],
  connectionsTableName: Option[String],
  environment: T,
)

object Config {
  import js.Dynamic.{global => g}

  def load() = loadTyped[js.Dictionary[String]]()
  def loadTyped[T]() = ConfigTyped[T](
    connectionsTableName = g.process.env.FUN_WEBSOCKET_CONNECTIONS_DYNAMODB_TABLE.asInstanceOf[js.UndefOr[String]].toOption,
    apiGatewayEndpoint = g.process.env.FUN_WEBSOCKET_API_GATEWAY_ENDPOINT.asInstanceOf[js.UndefOr[String]].toOption,
    environment = g.process.env.asInstanceOf[T]
  )
}
