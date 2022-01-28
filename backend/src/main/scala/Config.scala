package funstack.backend

case class Config(
  apiGatewayEndpoint: Option[String],
  connectionsTableName: Option[String],
)
object Config {
  import scala.scalajs.js
  import js.Dynamic.{global => g}
  def loadFromEnv() = Config(
    connectionsTableName = g.process.env.FUN_WEBSOCKET_CONNECTIONS_DYNAMODB_TABLE.asInstanceOf[js.UndefOr[String]].toOption,
    apiGatewayEndpoint = g.process.env.FUN_WEBSOCKET_API_GATEWAY_ENDPOINT.asInstanceOf[js.UndefOr[String]].toOption
  )
}
