package funstack.web

import funstack.core.StringSerdes
import colibri.Observable
import sloth.{Request, RequestTransport}
import mycelium.core.client.SendType
import cats.effect.Async
import scala.concurrent.duration._

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

private object WsTransport {

  def apply[PickleType, F[_]: Async](send: (List[String], PickleType, SendType, FiniteDuration) => Option[Future[Either[Unit, PickleType]]]): RequestTransport[PickleType, F] =
    new RequestTransport[PickleType, F] {
      def apply(request: Request[PickleType]): F[PickleType] =
        Async[F].async[PickleType](cb =>
          send(request.path, request.payload, SendType.WhenConnected, 30.seconds) match {
            case Some(response) => response.onComplete {
              case util.Success(Right(value)) => cb(Right(value))
              case util.Success(Left(error))  => cb(Left(new Exception(s"Request failed: $error")))
              case util.Failure(ex)           => cb(Left(ex))
            }
            case None => cb(Left(new Exception("Websocket connection not started. You need to call 'ws.start'.")))
          }
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

