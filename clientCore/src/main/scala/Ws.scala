package funstack.client.core

import cats.effect.{unsafe, IO, LiftIO, Sync}
import colibri._
import funstack.client.core.auth.{Auth, User}
import funstack.client.core.helper.EventSubscriber
import funstack.core.{CanSerialize, SubscriptionEvent}
import funstack.ws.core.{ClientMessageSerdes, ServerMessageSerdes}
import mycelium.core.client.{WebsocketClient, WebsocketClientConfig}
import mycelium.js.client.JsWebsocketConnection

import scala.concurrent.Future
import scala.concurrent.duration._

class Ws(ws: WsAppConfig, auth: Option[Auth]) {

  import org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits.global

  private var wsClientConnection: Option[(Cancelable, WebsocketClient[String, SubscriptionEvent, String])] = None
  private def wsClient                                                                                     = wsClientConnection.map(_._2)

  private val eventSubscriber = new EventSubscriber(x => wsClient.foreach(_.rawSend(x)))

  val start: IO[Cancelable] = startF[IO]

  def startF[F[_]: Sync]: F[Cancelable] = Sync[F].delay {
    wsClientConnection.foreach(_._1.unsafeCancel())
    val (cancelable, client) = createWsClient()
    wsClientConnection = Some((cancelable, client))
    cancelable
  }

  def transport[T: CanSerialize] = WsTransport[T]((a, b, c, d) =>
    wsClient.map(_.send(a, CanSerialize[T].serialize(b), c, d).flatMap {
      case Right(value) => Future.fromTry(CanSerialize[T].deserialize(value).map(Right.apply).toTry)
      case Left(value)  => Future.fromTry(CanSerialize[T].deserialize(value).map(Left.apply).toTry)
    }),
  )

  // TODO Async
  def transportF[T: CanSerialize, F[_]: LiftIO] = transport[T].map(LiftIO[F].liftIO)

  def transportFuture[T: CanSerialize] = transport[T].map(_.unsafeToFuture()(unsafe.IORuntime.global))

  def streamsTransport[T: CanSerialize] = WsTransport.subscriptions(eventSubscriber)

  private def createWsClient(): (Cancelable, WebsocketClient[String, SubscriptionEvent, String]) = {
    import ClientMessageSerdes.implicits._
    import ServerMessageSerdes.implicits._

    val wsClient = WebsocketClient[String, SubscriptionEvent, String](
      new JsWebsocketConnection[String],
      WebsocketClientConfig(
        // idle timeout is 10 minutes on api gateway: https://docs.aws.amazon.com/apigateway/latest/developerguide/limits.html
        // TODO should this be more often to work through all the possible proxies in between?
        pingInterval = 9.minutes,
      ),
      eventSubscriber,
    )

    var currentUser: Option[User]        = None
    var connectionCancelable: Cancelable = Cancelable.empty

    val baseUrl = ws.url.replaceFirst("/$", "")

    def runServer(): Unit = {
      connectionCancelable.unsafeCancel()
      connectionCancelable =
        if (currentUser.isEmpty && !ws.allowUnauthenticated) Cancelable.empty
        else {
          val tokenRunCancel = Cancelable.variable()
          val clientCancel   = wsClient.runFromFuture { () =>
            currentUser
              .fold(IO.pure(baseUrl))(_.token.map { token =>
                s"${baseUrl}?token=${token.access_token}"
              })
              .unsafeToFuture()(unsafe.IORuntime.global)
          }
          Cancelable.composite(tokenRunCancel, Cancelable(clientCancel.cancel))
        }
    }

    val subscriptionCancelable = auth match {
      case None       =>
        runServer()
        Cancelable.empty
      case Some(auth) =>
        auth.currentUser.unsafeForeach { user =>
          val prevUser = currentUser
          currentUser = user
          if (connectionCancelable == Cancelable.empty || prevUser.map(_.info.sub) != user.map(_.info.sub)) runServer()
        }
    }

    (Cancelable.composite(Cancelable(() => connectionCancelable.unsafeCancel()), subscriptionCancelable), wsClient)
  }
}
