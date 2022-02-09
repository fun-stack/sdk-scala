package funstack.backend

import scala.scalajs.js
import cats.implicits._

@js.native
trait FunDevEnvironment extends js.Object {
  def send_subscription(subscriptionKey: String, data: String): Unit = js.native
}

case class ConfigTyped[T](
  devEnvironment: Option[FunDevEnvironment],
  cognitoUserPoolId: Option[String],
  apiGatewayEndpoint: Option[String],
  subscriptionsTableName: Option[String],
  environment: T,
)

object Config {
  import js.Dynamic.{global => g}

  def load() = loadTyped[js.Dictionary[String]]()
  def loadTyped[T]() = ConfigTyped[T](
    // lambda-server sets fun_dev_environment to send events in development case. We fallback to this, incase the environment variables from the terraform module are not set.
    devEnvironment = Either.catchNonFatal(g.fun_dev_environment.asInstanceOf[js.UndefOr[FunDevEnvironment]].toOption).toOption.flatten,
    cognitoUserPoolId = g.process.env.FUN_AUTH_COGNITO_USER_POOL_ID.asInstanceOf[js.UndefOr[String]].toOption,
    subscriptionsTableName = g.process.env.FUN_WEBSOCKET_SUBSCRIPTIONS_DYNAMODB_TABLE.asInstanceOf[js.UndefOr[String]].toOption,
    apiGatewayEndpoint = g.process.env.FUN_WEBSOCKET_API_GATEWAY_ENDPOINT.asInstanceOf[js.UndefOr[String]].toOption,
    environment = g.process.env.asInstanceOf[T]
  )
}
