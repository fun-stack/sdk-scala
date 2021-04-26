package funstack.web

import colibri.Cancelable
import sloth.{Request, RequestTransport}
import mycelium.js.client.JsWebsocketConnection
import mycelium.js.core.JsMessageBuilder
import mycelium.core.client.{SendType, IncidentHandler, WebsocketClientConfig, WebsocketClient}
import mycelium.core.message.{ServerMessage, ClientMessage}
import chameleon.{Serializer, Deserializer}
import cats.effect.IO
import scala.concurrent.duration._
import colibri.Observable
import funstack.core._

case class WebsocketConfig(
    baseUrl: Url,
)

class Websocket(val config: WebsocketConfig) {
  import scala.concurrent.ExecutionContext.Implicits.global
  private implicit val cs = IO.contextShift(global)

  def transport[Event, Failure, PickleType](implicit
      serializer: Serializer[ClientMessage[PickleType], String],
      deserializer: Deserializer[ServerMessage[PickleType, Event, Failure], String],
  ): RequestTransport[PickleType, IO] =
    new RequestTransport[PickleType, IO] {
      private val client = WebsocketClient.withPayload[String, PickleType, Event, Failure](
        new JsWebsocketConnection[String],
        WebsocketClientConfig(pingInterval = 7.days),
        new IncidentHandler[Event] {
          override def onConnect(): Unit                   = println("CONNECT")
          override def onClose(): Unit                     = println("DISCONNECT")
          override def onEvents(events: List[Event]): Unit = println("EVENT " + events)
        },
      )
      private var currentUser: Option[User] = None
      println("GO?")
      Fun.auth.currentUser
        .scan[(Cancelable, Option[User])]((Cancelable.empty, None)) { (current, user) =>
          currentUser = user
          val (cancelable, prevUser) = current
          println(prevUser.toString)
          println(user.toString)
          val newCancelable = (prevUser, user) match {
            case (Some(prevUser), Some(user)) if prevUser.info.sub == user.info.sub =>
              cancelable
            case (_, Some(user)) =>
              println("RUN?")
              cancelable.cancel()
              client.run { () =>
                println("GO " + s"${config.baseUrl.value}/?token=${currentUser.get.token.access_token}")
                s"${config.baseUrl.value}/?token=${currentUser.get.token.access_token}"
              }
              Cancelable.empty
            case (_, None) =>
              cancelable.cancel()
              Cancelable.empty
          }

          (newCancelable, user)
        }
        .subscribe(colibri.Observer.empty)

      def apply(request: Request[PickleType]): IO[PickleType] =
        IO.fromFuture(IO(client.send(request.path, request.payload, SendType.WhenConnected, 30.seconds)))
          .flatMap {
            case Right(value) => IO.pure(value)
            case Left(error)  => IO.raiseError(new Exception(s"Request failed: $error"))
          }
    }
}
