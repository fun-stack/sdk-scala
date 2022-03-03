package funstack.web

import funstack.ws.core.{ClientMessageSerdes, ServerMessageSerdes}
import funstack.core.{CanSerialize, SubscriptionEvent}
import funstack.web.helper.EventSubscriber

import colibri._
import mycelium.js.client.JsWebsocketConnection
import mycelium.core.{Cancelable => MyceliumCancelable}
import mycelium.core.client.{WebsocketClient, WebsocketClientConfig}

import cats.effect.{Async, IO}

import scala.concurrent.duration._
import scala.concurrent.Future

import scala.concurrent.ExecutionContext.Implicits.global

class Ws(ws: WsAppConfig, auth: Option[Auth[IO]]) {

  private var wsClientConnection: Option[(Cancelable, WebsocketClient[String, SubscriptionEvent, String])] = None
  private def wsClient                                                                                     = wsClientConnection.map(_._2)

  val start: IO[Unit] = IO {
    wsClientConnection.foreach(_._1.cancel())
    wsClientConnection = Some(createWsClient())
  }

  private val eventSubscriber = new EventSubscriber(x => wsClient.foreach(_.rawSend(x)))

  def transport[T: CanSerialize]               = transportF[T, IO]
  def transportF[T: CanSerialize, F[_]: Async] = WsTransport[T, F]((a, b, c, d) =>
    wsClient.map(_.send(a, CanSerialize[T].serialize(b), c, d).flatMap {
      case Right(value) => Future.fromTry(CanSerialize[T].deserialize(value).map(Right.apply).toTry)
      case Left(value)  => Future.fromTry(CanSerialize[T].deserialize(value).map(Left.apply).toTry)
    }),
  )
  def transportFuture[T: CanSerialize]         = transport[T].map(_.unsafeToFuture())
  def streamsTransport[T: CanSerialize]        = WsTransport.subscriptions(eventSubscriber)

  private def createWsClient(): (Cancelable, WebsocketClient[String, SubscriptionEvent, String]) = {
    import ServerMessageSerdes.implicits._, ClientMessageSerdes.implicits._

    val wsClient = WebsocketClient[String, SubscriptionEvent, String](
      new JsWebsocketConnection[String],
      WebsocketClientConfig(
        // idle timeout is 10 minutes on api gateway: https://docs.aws.amazon.com/apigateway/latest/developerguide/limits.html
        // TODO should this be more often to work through all the possible proxies in between?
        pingInterval = 9.minutes,
      ),
      eventSubscriber,
    )

    var currentUser: Option[User] = None
    var connectionCancelable      = MyceliumCancelable.empty

    def runServer(): Unit = {
      connectionCancelable.cancel()
      connectionCancelable =
        if (currentUser.isEmpty && !ws.allowUnauthenticated) MyceliumCancelable.empty
        else
          wsClient.run { () =>
            val tokenParam = currentUser.fold("")(user => s"?token=${user.token.access_token}")
            s"${ws.url}/${tokenParam}"
          }
    }

    val subscriptionCancelable = auth match {
      case None       =>
        runServer()
        Cancelable.empty
      case Some(auth) =>
        auth.currentUser.foreach { user =>
          val prevUser = currentUser
          currentUser = user
          if (connectionCancelable == MyceliumCancelable.empty || prevUser.map(_.info.sub) != user.map(_.info.sub)) runServer()
        }
    }

    (Cancelable.composite(Cancelable(() => connectionCancelable.cancel()), subscriptionCancelable), wsClient)
  }
}
