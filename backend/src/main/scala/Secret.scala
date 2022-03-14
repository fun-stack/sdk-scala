package funstack.backend

import scala.scalajs.js
import scala.scalajs.js.JSConverters._
import cats.effect.IO
import cats.implicits._

object Secret {
  private implicit val cs = IO.contextShift(scala.concurrent.ExecutionContext.global)

  case class SecretNotFound(secret: String) extends Exception(s"Secret not found: $secret")

  import facade.amazonaws.services.ssm._
  import facade.amazonaws.services.secretsmanager._

  private lazy val ssmClient            = new SSM()
  private lazy val secretsManagerClient = new SecretsManager()

  def fromSSMParameter(parameterName: String): IO[String] = {
    val params = GetParameterRequest(Name = parameterName, WithDecryption = true)
    IO.fromFuture(IO(ssmClient.getParameterFuture(params))).map { result =>
      result.Parameter.flatMap(_.Value).getOrElse(throw SecretNotFound(parameterName))
    }
  }

  def fromSecretsManager(secretId: String): IO[String] = {
    val params = GetSecretValueRequest(SecretId = secretId)
    IO.fromFuture(IO(secretsManagerClient.getSecretValueFuture(params))).map { result =>
      result.SecretString.getOrElse(throw SecretNotFound(secretId))
    }
  }
}
