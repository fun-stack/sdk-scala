package funstack.web

import funstack.core.StringSerdes
import colibri.{Subject, Observable}
import cats.effect.{IO, Async}
import sloth.{Client, ClientException}
import mycelium.core.message._
import chameleon.{Serializer, Deserializer}
import scala.concurrent.Future

class Ws[Event](ws: WsAppConfig, auth: Option[Auth[IO]]) {
  import scala.concurrent.ExecutionContext.Implicits.global

  private val eventsSubject     = Subject.publish[Event]
  def events: Observable[Event] = eventsSubject

  def client[PickleType](implicit
      serializer: Serializer[ClientMessage[PickleType], StringSerdes],
      deserializer: Deserializer[ServerMessage[PickleType, Event, Unit], StringSerdes],
  ) = clientF[PickleType, IO]

  def clientF[PickleType, F[_]: Async](implicit
      serializer: Serializer[ClientMessage[PickleType], StringSerdes],
      deserializer: Deserializer[ServerMessage[PickleType, Event, Unit], StringSerdes],
  ) = Client[PickleType, F](WebsocketTransport[Event, Unit, PickleType, F](ws, auth, eventsSubject))

  def clientFuture[PickleType](implicit
      serializer: Serializer[ClientMessage[PickleType], StringSerdes],
      deserializer: Deserializer[ServerMessage[PickleType, Event, Unit], StringSerdes],
  ) = Client[PickleType, Future](WebsocketTransport[Event, Unit, PickleType, IO](ws, auth, eventsSubject).map(_.unsafeToFuture()))
}
