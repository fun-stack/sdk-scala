package funstack.web

import cats.effect.Async
import sloth.{Client, ClientException}
import mycelium.core.message._
import chameleon.{Serializer, Deserializer}

class Api(api: ApiAppConfig) {
  val ws = new Websocket(WebsocketConfig(baseUrl = api.url, allowUnauthenticated = api.allowUnauthenticated))

  def wsClient[PickleType, F[_]: Async](implicit
      serializer: Serializer[ClientMessage[PickleType], String],
      deserializer: Deserializer[ServerMessage[PickleType, String, String], String],
  ) = Client[PickleType, F, ClientException](ws.transport[String, String, PickleType, F])
}
