package funstack.web

import funstack.core.StringSerdes
import colibri.Observable
import sloth.{Request, RequestTransport}
import mycelium.core.client.{SendType, WebsocketClient}
import cats.effect.Async
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

  def subscriptions(eventSubscriber: EventSubscriber): RequestTransport[StringSerdes, Observable] =
    new RequestTransport[StringSerdes, Observable] {
      def apply(request: Request[StringSerdes]): Observable[StringSerdes] = {
        val subscriptionKey = s"${request.path.mkString("/")}/${request.payload.value}"
        Observable.create(eventSubscriber.subscribe(subscriptionKey, _))
      }
    }
}

