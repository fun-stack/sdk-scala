package funstack.backend

import scala.scalajs.js
import scala.scalajs.js.JSConverters._
import cats.effect.IO
import cats.implicits._

@js.native
trait FunDevEnvironment extends js.Object {
  val sendSubscription: js.UndefOr[js.Function2[String, String, Unit]] = js.native
  val getEmail: js.UndefOr[js.Function1[String, String]]               = js.native
}

case class FunConfig(
  devEnvironment: Option[FunDevEnvironment],
  cognitoUserPoolId: Option[String],
  eventsSnsTopic: Option[String],
)

case class Config(
  fun: FunConfig,
  environment: js.Dictionary[String],
) {
  private implicit val cs = IO.contextShift(scala.concurrent.ExecutionContext.global)

  private val secretsPrefixSSM            = "FUN_SECRETS_SSM_PARAMETER_"
  private val secretsPrefixSecretsManager = "FUN_SECRETS_SECRETSMANAGER_"

  def environmentTyped[Env <: js.Any]: Env = environment.asInstanceOf[Env]

  def environmentWithSecrets: IO[js.Dictionary[String]] = environment
    .asInstanceOf[js.Dictionary[String]]
    .toList
    .parTraverse { case (key, name) =>
      key match {
        case key if key.startsWith(secretsPrefixSSM)            =>
          val newKey = key.substring(secretsPrefixSSM.length)
          Secret.fromSSMParameter(name).map(newKey -> _)
        case key if key.startsWith(secretsPrefixSecretsManager) =>
          val newKey = key.substring(secretsPrefixSecretsManager.length)
          Secret.fromSecretsManager(name).map(newKey -> _)
        case _                                                  =>
          IO.pure(key -> name)
      }
    }
    .map(_.toMap.toJSDictionary)

  def environmentWithSecretsTyped[Env <: js.Any]: IO[Env] = environmentWithSecrets.map(_.asInstanceOf[Env])
}

object Config {
  import js.Dynamic.{global => g}

  def load() = Config(
    fun = FunConfig(
      // lambda-server sets fun_dev_environment to send events in development case. We fallback to this, incase the environment variables from the terraform module are not set.
      devEnvironment = Either.catchNonFatal(g.fun_dev_environment.asInstanceOf[js.UndefOr[FunDevEnvironment]].toOption).toOption.flatten,
      cognitoUserPoolId = g.process.env.FUN_AUTH_COGNITO_USER_POOL_ID.asInstanceOf[js.UndefOr[String]].toOption,
      eventsSnsTopic = g.process.env.FUN_EVENTS_SNS_OUTPUT_TOPIC.asInstanceOf[js.UndefOr[String]].toOption,
    ),
    environment = g.process.env.asInstanceOf[js.Dictionary[String]],
  )
}
