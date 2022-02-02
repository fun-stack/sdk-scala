package funstack.web

import funstack.core.{SubscriptionEvent, StringSerdes}
import sloth._
import chameleon.{Serializer, Deserializer}
import scala.concurrent.Future

import colibri._
import mycelium.js.client.JsWebsocketConnection
import mycelium.core.client.{WebsocketClientConfig, WebsocketClient, WebsocketClientWithPayload}
import mycelium.core.message.{ServerMessage, ClientMessage}
import cats.effect.{Async, IO}
import scala.concurrent.duration._

import scala.concurrent.ExecutionContext.Implicits.global

class Ws(ws: WsAppConfig, auth: Option[Auth[IO]]) {
  import SlothInstances._
  import MyceliumInstances._

  private var wsClient: Option[WebsocketClient[StringSerdes, SubscriptionEvent, Unit]] = None

  def start(implicit
    serializer: Serializer[ClientMessage[StringSerdes], StringSerdes],
    deserializer: Deserializer[ServerMessage[StringSerdes, SubscriptionEvent, Unit], StringSerdes],
  ): IO[Unit] = IO {
    wsClient match {
      case None =>
        wsClient = Some(createWsClient())
      case Some(_) => ()
    }
  }

  private val eventSubscriber = new EventSubscriber(x => wsClient.foreach(_.rawSend(x)))

  def client = clientF[IO]

  def clientF[F[_]: Async] = Client[StringSerdes, F](WebsocketTransport[StringSerdes, F]((a,b,c,d) => wsClient.map(_.send(a,b,c,d))))

  def clientFuture = Client[StringSerdes, Future](WebsocketTransport[StringSerdes, IO]((a,b,c,d) => wsClient.map(_.send(a,b,c,d))).map(_.unsafeToFuture()))

  val subscriptionsClient = Client[StringSerdes, Observable](WebsocketTransport.subscriptions(eventSubscriber))

  private def createWsClient()(implicit
    serializer: Serializer[ClientMessage[StringSerdes], StringSerdes],
    deserializer: Deserializer[ServerMessage[StringSerdes, SubscriptionEvent, Unit], StringSerdes],
  ): WebsocketClientWithPayload[StringSerdes, StringSerdes, SubscriptionEvent, Unit] = {
    val wsClient = WebsocketClient[StringSerdes, SubscriptionEvent, Unit](
        new JsWebsocketConnection[StringSerdes],
        WebsocketClientConfig(
          // idle timeout is 10 minutes on api gateway: https://docs.aws.amazon.com/apigateway/latest/developerguide/limits.html
          //TODO should this be more often to work through all the possible proxies in between?
          pingInterval = 9.minutes,
        ),
        eventSubscriber
      )
  var currentUser: Option[User] = None
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
   wsClient
  }
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
  implicit val observableClientHandler: ClientHandler[Observable] = new ClientHandler[Observable] {
    def raiseFailure[B](failure: ClientFailure): Observable[B] = Observable.failure(failure.toException)
    def eitherMap[A, B](fa: Observable[A])(f: A => Either[ClientFailure,B]): Observable[B] = fa.mapEither(a => f(a).left.map(_.toException))
  }
}
