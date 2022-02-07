package funstack.backend

import cats.implicits._
import chameleon.Serializer
import funstack.core.{SubscriptionEvent, StringSerdes}
import mycelium.core.message.ServerMessage

object Fun {
  val config = Config.load()
  val ws =
    (config.apiGatewayEndpoint, config.subscriptionsTableName).mapN { (endpoint, subscriptionsTable) =>
      new WsOperationsAWS(apiGatewayEndpoint = endpoint, subscriptionsTable = subscriptionsTable)
    }
      .orElse(config.devEnvironment.map(env => new WsOperationsDev(env.send_subscription)))
      .map(new Ws(_))

  val auth = config.cognitoUserPoolId.map(new Auth(_))
}

object FunAll {
  case class MissingModuleException(name: String) extends Exception(s"Missing module: $name")

  val config = Fun.config
  val ws = Fun.ws.getOrElse(throw MissingModuleException("ws"))
  val auth = Fun.auth.getOrElse(throw MissingModuleException("auth"))
}
