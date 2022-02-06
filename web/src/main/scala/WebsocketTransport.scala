package funstack.web

import funstack.core.{SubscriptionEvent, StringSerdes}
import colibri.{Observer, Observable, Cancelable}
import sloth.{Request, RequestTransport}
import mycelium.js.client.JsWebsocketConnection
import mycelium.core.client.{SendType, IncidentHandler, WebsocketClientConfig, WebsocketClient}
import mycelium.core.message.{ServerMessage, ClientMessage}
import chameleon.{Serializer, Deserializer}
import cats.effect.{Async, IO}
import scala.concurrent.duration._

import scala.concurrent.ExecutionContext.Implicits.global

object WebsocketTransport {

  def apply[PickleType, F[_]: Async](client: WebsocketClient[PickleType, _, _]): RequestTransport[PickleType, F] =
    new RequestTransport[PickleType, F] {
      def apply(request: Request[PickleType]): F[PickleType] =
        Async[F].async[PickleType](cb =>
          client.send(request.path, request.payload, SendType.WhenConnected, 30.seconds).onComplete {
            case util.Success(Right(value)) => cb(Right(value))
            case util.Success(Left(error))  => cb(Left(new Exception(s"Request failed: $error")))
            case util.Failure(ex)           => cb(Left(ex))
          },
        )
    }

  def subscriptions(client: WebsocketClient[StringSerdes, _, _], events: Observable[ControlEvent]): RequestTransport[StringSerdes, Observable] =
    new RequestTransport[StringSerdes, Observable] {
      def apply(request: Request[StringSerdes]): Observable[StringSerdes] = {
        val subscriptionKey = s"${request.path.mkString("/")}/${request.payload.value}"

        val subscribePayload = StringSerdes(s"""{"__action": "subscribe", "subscription_key": "${subscriptionKey}" }""")
        val unsubscribePayload = StringSerdes(s"""{"__action": "unsubscribe", "subscription_key": "${subscriptionKey}" }""")

        var cancelable: Cancelable = Cancelable.empty
        def subscribe(): Unit = {
          def inner(): Cancelable = {
            client.rawSend(subscribePayload)
            Cancelable(() => client.rawSend(unsubscribePayload))
          }

          cancelable.cancel()
          cancelable = inner()
        }

        events
          .doOnSubscribe { () =>
            println("SUBSCRIBE")
            subscribe()
            Cancelable(() => cancelable.cancel())
          }
          .doOnNext {
            case ControlEvent.Disconnected =>
              println("GOT DISCONNECT")
              cancelable = Cancelable.empty
            case ControlEvent.Connected =>
              println("GOT CONNECT")
              subscribe()
            case e =>
              println("GOT EVENT " + e)
              ()
          }
          .collect { case ControlEvent.Subscription(SubscriptionEvent(`subscriptionKey`, body)) => body }
          .publish
          .refCount
      }
    }
}

