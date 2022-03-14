package funstack.lambda.ws.eventauthorizer

import scala.scalajs.js
import cats.implicits._

@js.native
private[eventauthorizer] trait FunDevEnvironment extends js.Object {
  def send_connection(@annotation.nowarn connectionId: String, @annotation.nowarn data: String): Unit = js.native
}

private[eventauthorizer] case class FunConfig(
  devEnvironment: Option[FunDevEnvironment],
  eventsSnsTopic: Option[String],
)
private[eventauthorizer] object FunConfig {
  import js.Dynamic.{global => g}

  def load() = FunConfig(
    // lambda-server sets fun_dev_environment to send events in development case. We fallback to this, incase the environment variables from the terraform module are not set.
    devEnvironment = Either.catchNonFatal(g.fun_dev_environment.asInstanceOf[js.UndefOr[FunDevEnvironment]].toOption).toOption.flatten,
    eventsSnsTopic = g.process.env.FUN_EVENTS_SNS_OUTPUT_TOPIC.asInstanceOf[js.UndefOr[String]].toOption,
  )
}
