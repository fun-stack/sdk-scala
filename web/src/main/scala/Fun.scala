package funstack.web

import cats.effect.IO
import mycelium.js.core.JsMessageBuilder
import mycelium.core.message._
import chameleon.{Serializer, Deserializer}
import funstack.core._

import org.scalajs.dom

object Fun {
  val auth = AppConfig.auth.map(Auth.apply)
  val api  = AppConfig.api.map(new Api(_))
}
