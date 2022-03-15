package funstack.web

import funstack.core.StringSerdes
import colibri.{Observer, Cancelable}
import sloth.{Request, RequestTransport}
import mycelium.js.client.JsWebsocketConnection
import mycelium.core.client.{SendType, IncidentHandler, WebsocketClientConfig, WebsocketClient}
import mycelium.core.message.{ServerMessage, ClientMessage}
import chameleon.{Serializer, Deserializer}
import cats.effect.{Async, IO}
import scala.concurrent.duration._

import scala.concurrent.ExecutionContext.Implicits.global

object WebsocketTransport {
  import MyceliumInstances._

  def apply[Event, Failure, PickleType, F[_]: Async](config: WsAppConfig, auth: Option[Auth[IO]], observer: Observer[Event])(implicit
      serializer: Serializer[ClientMessage[PickleType], StringSerdes],
      deserializer: Deserializer[ServerMessage[PickleType, Event, Failure], StringSerdes],
  ): RequestTransport[PickleType, F] =
    new RequestTransport[PickleType, F] {
      private val client = WebsocketClient.withPayload[StringSerdes, PickleType, Event, Failure](
        new JsWebsocketConnection[StringSerdes],
        WebsocketClientConfig(
          // idle timeout is 10 minutes on api gateway: https://docs.aws.amazon.com/apigateway/latest/developerguide/limits.html
          pingInterval = 9.minutes,
        ),
        new IncidentHandler[Event] {
          override def onConnect(): Unit           = ()
          override def onClose(): Unit             = ()
          override def onEvent(event: Event): Unit = observer.onNext(event)
        },
      )
      private var currentUser: Option[User] = None
      //TODO..
      auth.fold(Cancelable(client.run(config.url).cancel))(
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
                  s"${config.url}/?token=${currentUser.fold("")(_.token.access_token)}"
                }
                Cancelable(cancel.cancel)
              case (_, None) if config.allowUnauthenticated =>
                cancelable.cancel()
                val cancel = client.run(config.url)
                Cancelable(cancel.cancel)
              case (_, None) =>
                cancelable.cancel()
                Cancelable.empty
            }

            (newCancelable, user)
          }.subscribe(),
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

private object MyceliumInstances {
  import mycelium.js.core.JsMessageBuilder
  import scala.concurrent.Future

  implicit val base64JsMessageBuilder: JsMessageBuilder[StringSerdes] = new JsMessageBuilder[StringSerdes] {
    import JsMessageBuilder._

    def pack(msg: StringSerdes): Message = JsMessageBuilder.JsMessageBuilderString.pack(msg.value)
    def unpack(m: Message): Future[Option[StringSerdes]] = JsMessageBuilder.JsMessageBuilderString.unpack(m).map(_.map(StringSerdes(_)))
  }
}
