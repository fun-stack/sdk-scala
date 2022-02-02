package funstack.web

import cats.effect.IO
import chameleon._
import funstack.core.StringSerdes
import mycelium.core.message.{ServerMessage, ClientMessage}

object Fun {
  val config              = AppConfig.load()
  val auth                = config.auth.map(new Auth[IO](_, config.website)).toOption
  def wsWithEvents[Event](implicit
      serializer: Serializer[ClientMessage[StringSerdes], StringSerdes],
      deserializer: Deserializer[ServerMessage[StringSerdes, Event, Unit], StringSerdes],
  ) = config.ws.map(new Ws[Event](_, auth)).toOption
  def ws(implicit
      serializer: Serializer[ClientMessage[StringSerdes], StringSerdes],
      deserializer: Deserializer[ServerMessage[StringSerdes, Unit, Unit], StringSerdes],
    )                  = wsWithEvents[Unit]
  val http                = config.http.map(new Http(_, auth)).toOption
}
