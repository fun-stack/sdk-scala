package funstack.client.core

import cats.effect.IO
import colibri.Observable
import funstack.client.core.helper.EventSubscriber
import funstack.core.CanSerialize
import mycelium.core.client.SendType
import sloth.{Request, RequestTransport}

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.scalajs.js

private object WsTransport {

  def apply[PickleType](
    send: (List[String], PickleType, SendType, FiniteDuration) => Option[Future[Either[PickleType, PickleType]]],
  ): RequestTransport[PickleType, IO] =
    new RequestTransport[PickleType, IO] {
      def apply(request: Request[PickleType]): IO[PickleType] =
        IO.fromFuture(IO(send(request.path, request.payload, SendType.WhenConnected, 30.seconds) match {
          case Some(response) => response
          case None           => Future.failed(new Exception("Websocket connection not started. You need to call 'Fun.ws.start'."))
        }))
          .flatMap {
            case Right(value) => IO.pure(value)
            case Left(error)  => IO.raiseError(new Exception(s"Request failed: $error"))
          }
    }

  def subscriptions[T: CanSerialize](eventSubscriber: EventSubscriber): RequestTransport[T, Observable] =
    new RequestTransport[T, Observable] {
      def apply(request: Request[T]): Observable[T] = {
        val subscriptionKey = s"${request.path.mkString("/")}/${js.Dynamic.global.escape(CanSerialize[T].serialize(request.payload))}"
        Observable.create[T](observer => eventSubscriber.subscribe(subscriptionKey, observer.contramapEither[String](CanSerialize[T].deserialize)))
      }
    }
}
