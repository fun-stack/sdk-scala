package funstack.web

import cats.effect.IO
import chameleon._
import funstack.core.{SubscriptionEvent, StringSerdes}
import mycelium.core.message.{ServerMessage, ClientMessage}

object Fun {
  val config              = AppConfig.load()
  val auth                = config.auth.map(new Auth[IO](_, config.website)).toOption
  def ws(implicit
      serializer: Serializer[ClientMessage[StringSerdes], StringSerdes],
      deserializer: Deserializer[ServerMessage[StringSerdes, SubscriptionEvent, Unit], StringSerdes],
    )                  = config.ws.map(new Ws(_, auth)).toOption
  val http                = config.http.map(new Http(_, auth)).toOption
}
