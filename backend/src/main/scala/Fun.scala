package funstack.backend

import cats.implicits._
import chameleon.Serializer
import funstack.core.{SubscriptionEvent, StringSerdes}
import mycelium.core.message.ServerMessage

object Fun {
  val config = Config.load()
  def ws(implicit serializer: Serializer[ServerMessage[Unit, SubscriptionEvent, Unit], StringSerdes]) =
    (config.apiGatewayEndpoint, config.connectionsTableName).mapN { (endpoint, table) =>
      new Ws(apiGatewayEndpoint = endpoint, tableName = table)
    }
}
