package funstack.web

import cats.effect.Async
import sloth.{Client, ClientException}
import mycelium.core.message._
import chameleon.{Serializer, Deserializer}
import funstack.core._

class Api(api: ApiAppConfig) {
  private val isLocalhost = api.domain.startsWith("localhost:") || api.domain == "localhost"
  private val protocol = if(isLocalhost) "ws" else "wss"
  val ws = new Websocket(WebsocketConfig(baseUrl = Url(s"${protocol}://${api.domain}"), allowUnauthenticated = api.allowUnauthenticated))

  def wsClient[PickleType, F[_]: Async](implicit
      serializer: Serializer[ClientMessage[PickleType], String],
      deserializer: Deserializer[ServerMessage[PickleType, String, String], String],
  ) = Client[PickleType, F, ClientException](ws.transport[String, String, PickleType, F])
}
