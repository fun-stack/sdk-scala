package funstack.lambda

import net.exoego.facade.aws_lambda._
import funstack.lambda.core.helper.facades._
import scala.concurrent.Future
import cats.effect.IO
import cats.data.Kleisli

import scala.scalajs.js

package object core {
  case class AuthInfo(sub: String)
  case class RequestOf[+T](event: T, context: Context, auth: Option[AuthInfo])
  type Request = RequestOf[Any]
}
