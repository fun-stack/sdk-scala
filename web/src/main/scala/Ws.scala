package funstack.web

import funstack.core.{SubscriptionEvent, StringSerdes}
import sloth._
import chameleon.{Serializer, Deserializer}
import scala.concurrent.Future

import colibri._
import mycelium.js.client.JsWebsocketConnection
import mycelium.core.{Cancelable => MyceliumCancelable}
import mycelium.core.client.{WebsocketClientConfig, WebsocketClient, WebsocketClientWithPayload}
import mycelium.core.message.{ServerMessage, ClientMessage}
import cats.effect.{Async, IO}
import scala.concurrent.duration._

import scala.concurrent.ExecutionContext.Implicits.global

class Ws(ws: WsAppConfig, auth: Option[Auth[IO]]) {
  import SlothInstances._
  import MyceliumInstances._

  private var wsClientConnection: Option[(Cancelable, WebsocketClient[StringSerdes, SubscriptionEvent, Unit])] = None
  private def wsClient = wsClientConnection.map(_._2)

  def start(implicit
    serializer: Serializer[ClientMessage[StringSerdes], StringSerdes],
    deserializer: Deserializer[ServerMessage[StringSerdes, SubscriptionEvent, Unit], StringSerdes],
  ): IO[Unit] = IO {
    wsClientConnection.foreach(_._1.cancel())
    wsClientConnection = Some(createWsClient())
  }

  private val eventSubscriber = new EventSubscriber(x => wsClient.foreach(_.rawSend(x)))

  def client = clientF[IO]

  def clientF[F[_]: Async] = Client[StringSerdes, F](WsTransport[StringSerdes, F]((a,b,c,d) => wsClient.map(_.send(a,b,c,d))))

  def clientFuture = Client[StringSerdes, Future](WsTransport[StringSerdes, IO]((a,b,c,d) => wsClient.map(_.send(a,b,c,d))).map(_.unsafeToFuture()))

  val subscriptionsClient = Client[StringSerdes, Observable](WsTransport.subscriptions(eventSubscriber))

  private def createWsClient()(implicit
    serializer: Serializer[ClientMessage[StringSerdes], StringSerdes],
    deserializer: Deserializer[ServerMessage[StringSerdes, SubscriptionEvent, Unit], StringSerdes],
  ): (Cancelable, WebsocketClientWithPayload[StringSerdes, StringSerdes, SubscriptionEvent, Unit]) = {
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
    var connectionCancelable = MyceliumCancelable.empty

    def runServer(): Unit = {
      connectionCancelable.cancel()
      connectionCancelable =
        if (currentUser.isEmpty && !ws.allowUnauthenticated) MyceliumCancelable.empty
        else wsClient.run { () =>
          val tokenParam = currentUser.fold("")(user => s"?token=${user.token.access_token}")
          s"${ws.url}/${tokenParam}"
        }
    }

    val subscriptionCancelable = auth match {
      case None =>
        runServer()
        Cancelable.empty
      case Some(auth) => auth.currentUser.foreach { user =>
        val prevUser = currentUser
        currentUser = user
        if (connectionCancelable == MyceliumCancelable.empty || prevUser.map(_.info.sub) != user.map(_.info.sub)) runServer()
      }
    }

    (Cancelable.composite(Cancelable(() => connectionCancelable.cancel()), subscriptionCancelable), wsClient)
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
