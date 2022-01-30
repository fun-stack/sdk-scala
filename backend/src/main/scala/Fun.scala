package funstack.backend

import cats.implicits._

object Fun {
  val config = Config.load()
  def wsWithEvents[Event] =
    (config.apiGatewayEndpoint, config.connectionsTableName).mapN { (endpoint, table) =>
      new Ws[Event](apiGatewayEndpoint = endpoint, tableName = table)
    }
  val ws = wsWithEvents[Unit]
}
