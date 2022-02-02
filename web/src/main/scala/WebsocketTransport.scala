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

  def apply[Event, Failure, PickleType, F[_]: Async](config: WsAppConfig, auth: Option[Auth[IO]], client: WebsocketClient[PickleType, Event, Failure])(implicit
      serializer: Serializer[ClientMessage[PickleType], StringSerdes],
      deserializer: Deserializer[ServerMessage[PickleType, Event, Failure], StringSerdes],
  ): RequestTransport[PickleType, F] =
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
}

