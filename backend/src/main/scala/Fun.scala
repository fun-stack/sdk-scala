package funstack.backend

import funstack.core.StringSerdes
import chameleon.Serializer
import mycelium.core.message.ServerMessage

object Fun {
  val config = Config.loadFromEnv()
  def ws[Event](implicit serializer: Serializer[ServerMessage[Unit, Event, Unit], StringSerdes]) = for {
    tableName <- config.connectionsTableName
    apiGatewayEndpoint <- config.apiGatewayEndpoint
  } yield new Ws[Event](apiGatewayEndpoint = apiGatewayEndpoint, tableName = tableName)
}
