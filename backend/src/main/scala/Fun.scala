package funstack.backend

import cats.implicits._

object Fun {
  val config = Config.load()

  val authOption = config.cognitoUserPoolId.map(new Auth(_))

  val wsOption =
    (config.apiGatewayEndpoint, config.subscriptionsTableName).mapN { (endpoint, subscriptionsTable) =>
      new WsOperationsAWS(apiGatewayEndpoint = endpoint, subscriptionsTable = subscriptionsTable)
    }
     .orElse(config.devEnvironment.map(env => new WsOperationsDev(env.send_subscription)))
     .map(new Ws(_))

  case class MissingModuleException(name: String) extends Exception(s"Missing module: $name")

  lazy val auth = authOption.getOrElse(throw MissingModuleException("auth"))
  lazy val ws   = wsOption.getOrElse(throw MissingModuleException("ws"))
}
