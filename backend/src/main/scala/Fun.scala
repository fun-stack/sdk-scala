package funstack.backend

object Fun {
  val config = Config.loadFromEnv()
  println("CONFIG: " + config)
  def ws[Event] = for {
    tableName <- config.connectionsTableName
    apiGatewayEndpoint <- config.apiGatewayEndpoint
  } yield new Ws[Event](apiGatewayEndpoint = apiGatewayEndpoint, tableName = tableName)
}