package funstack.web

import funstack.core.{SubscriptionEvent, StringSerdes}
import colibri.{Subject, Observable}
import cats.effect.{IO, Async}
import sloth.Client
import mycelium.core.message._
import chameleon.{Serializer, Deserializer}
import scala.concurrent.Future

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

private[web] sealed trait ControlEvent
private[web] object ControlEvent {
  case class Subscription(event: SubscriptionEvent) extends ControlEvent
  case object Connected extends ControlEvent
  case object Disconnected extends ControlEvent
}

class Ws(ws: WsAppConfig, auth: Option[Auth[IO]])(implicit
      serializer: Serializer[ClientMessage[StringSerdes], StringSerdes],
      deserializer: Deserializer[ServerMessage[StringSerdes, SubscriptionEvent, Unit], StringSerdes],
      ) {

  private val eventsSubject = Subject.publish[ControlEvent]

  import SlothInstances._
  val subscriptionsClient = Client[StringSerdes, Observable](WebsocketTransport.subscriptions(wsClient, eventsSubject))

  val client = clientF[IO]

  def clientF[F[_]: Async] = Client[StringSerdes, F](WebsocketTransport[StringSerdes, F](wsClient))

  def clientFuture = Client[StringSerdes, Future](WebsocketTransport[StringSerdes, IO](wsClient).map(_.unsafeToFuture()))

  import MyceliumInstances._
  private[web] lazy val wsClient = WebsocketClient[StringSerdes, SubscriptionEvent, Unit](
        new JsWebsocketConnection[StringSerdes],
        WebsocketClientConfig(
          // idle timeout is 10 minutes on api gateway: https://docs.aws.amazon.com/apigateway/latest/developerguide/limits.html
          pingInterval = 9.minutes,
        ),
        new IncidentHandler[SubscriptionEvent] {
          override def onConnect(): Unit           = eventsSubject.onNext(ControlEvent.Connected)
          override def onClose(): Unit             = eventsSubject.onNext(ControlEvent.Disconnected)
          override def onEvent(event: SubscriptionEvent): Unit = eventsSubject.onNext(ControlEvent.Subscription(event))
        },
      )
      private var currentUser: Option[User] = None
      //TODO..
      auth.fold(Cancelable(wsClient.run(ws.url).cancel))(
        _.currentUser
          .scan[(Cancelable, Option[User])]((Cancelable.empty, None)) { (current, user) =>
            currentUser = user
            val (cancelable, prevUser) = current
            val newCancelable = (prevUser, user) match {
              case (Some(prevUser), Some(user)) if prevUser.info.sub == user.info.sub =>
                cancelable
              case (_, Some(_)) =>
                cancelable.cancel()
                val cancel = wsClient.run { () =>
                  s"${ws.url}/?token=${currentUser.fold("")(_.token.access_token)}"
                }
                Cancelable(cancel.cancel)
              case (_, None) if ws.allowUnauthenticated =>
                cancelable.cancel()
                val cancel = wsClient.run(ws.url)
                Cancelable(cancel.cancel)
              case (_, None) =>
                cancelable.cancel()
                Cancelable.empty
            }

            (newCancelable, user)
          }.subscribe(),
      )
}
private object MyceliumInstances {
  import mycelium.js.core.JsMessageBuilder
  import scala.concurrent.Future

  implicit val stringSerdesJsMessageBuilder: JsMessageBuilder[StringSerdes] = new JsMessageBuilder[StringSerdes] {
    import JsMessageBuilder._

    def pack(msg: StringSerdes): Message = JsMessageBuilder.JsMessageBuilderString.pack(msg.value)
    def unpack(m: Message): Future[Option[StringSerdes]] = JsMessageBuilder.JsMessageBuilderString.unpack(m).map(_.map(StringSerdes(_)))
  }
}
private object SlothInstances {
  import sloth._

  implicit val observableClientHandler: ClientHandler[Observable] = new ClientHandler[Observable] {
    def raiseFailure[B](failure: ClientFailure): Observable[B] = Observable.failure(failure.toException)
    def eitherMap[A, B](fa: Observable[A])(f: A => Either[ClientFailure,B]): Observable[B] = fa.mapEither(a => f(a).left.map(_.toException))
  }
}
