package funstack.web

import cats.effect.{IO, Async}
import sloth.{Client, ClientException}
import mycelium.core.message._
import chameleon.{Serializer, Deserializer}
import scala.concurrent.{ExecutionContext, Future}

class Ws(api: WsAppConfig, auth: Option[Auth[IO]]) {
  val ws = new Websocket(WebsocketConfig(baseUrl = api.url, allowUnauthenticated = api.allowUnauthenticated))

  def client[PickleType](implicit
      serializer: Serializer[ClientMessage[PickleType], String],
      deserializer: Deserializer[ServerMessage[PickleType, String, String], String],
  ) = clientF[PickleType, IO]

  def clientF[PickleType, F[_]: Async](implicit
      serializer: Serializer[ClientMessage[PickleType], String],
      deserializer: Deserializer[ServerMessage[PickleType, String, String], String],
  ) = Client[PickleType, F, ClientException](ws.transport[String, String, PickleType, F](auth))

  def clientFuture[PickleType](implicit
      serializer: Serializer[ClientMessage[PickleType], String],
      deserializer: Deserializer[ServerMessage[PickleType, String, String], String],
      ec: ExecutionContext,
  ) = Client[PickleType, Future, ClientException](ws.transport[String, String, PickleType, IO](auth).map(_.unsafeToFuture()))
}
