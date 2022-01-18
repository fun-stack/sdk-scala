package funstack.web

import colibri.Cancelable
import sloth.{Request, RequestTransport}
import mycelium.js.client.JsWebsocketConnection
import mycelium.core.client.{SendType, IncidentHandler, WebsocketClientConfig, WebsocketClient}
import mycelium.core.message.{ServerMessage, ClientMessage}
import chameleon.{Serializer, Deserializer}
import cats.effect.{Async, IO}
import scala.concurrent.duration._

case class WebsocketConfig(
    baseUrl: String,
    allowUnauthenticated: Boolean,
)

class Websocket(config: WebsocketConfig) {
  import scala.concurrent.ExecutionContext.Implicits.global

  def transport[Event, Failure, PickleType, F[_]: Async](auth: Option[Auth[IO]])(implicit
      serializer: Serializer[ClientMessage[PickleType], String],
      deserializer: Deserializer[ServerMessage[PickleType, Event, Failure], String],
  ): RequestTransport[PickleType, F] =
    new RequestTransport[PickleType, F] {
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
      auth.fold(Cancelable(client.run(s"${config.baseUrl}/?token=anon").cancel))(
        _.currentUser
          .scan[(Cancelable, Option[User])]((Cancelable.empty, None)) { (current, user) =>
            currentUser = user
            val (cancelable, prevUser) = current
            val newCancelable = (prevUser, user) match {
              case (Some(prevUser), Some(user)) if prevUser.info.sub == user.info.sub =>
                cancelable
              case (_, Some(_)) =>
                cancelable.cancel()
                val cancel = client.run { () =>
                  s"${config.baseUrl}/?token=${currentUser.get.token.access_token}"
                }
                Cancelable(cancel.cancel)
              case (_, None) if config.allowUnauthenticated =>
                cancelable.cancel()
                val cancel = client.run(s"${config.baseUrl}/?token=anon")
                Cancelable(cancel.cancel)
              case (_, None) =>
                cancelable.cancel()
                Cancelable.empty
            }

            (newCancelable, user)
          }
          .subscribe(colibri.Observer.empty),
      )

      def apply(request: Request[PickleType]): F[PickleType] =
        Async[F].async[PickleType](cb =>
          client.send(request.path, request.payload, SendType.WhenConnected, 30.seconds).onComplete {
            case util.Success(Right(value)) => cb(Right(value))
            case util.Success(Left(error))  => cb(Left(new Exception(s"Request failed: $error")))
            case util.Failure(ex)           => cb(Left(ex))
          },
        )
    }
}
