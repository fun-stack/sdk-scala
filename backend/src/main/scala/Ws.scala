package funstack.backend

import funstack.core.{SubscriptionEvent, StringSerdes}
import facade.amazonaws.services.sns._
import scala.scalajs.js
import sloth._

import mycelium.core.message.{Notification, ServerMessage}

import cats.data.Kleisli
import cats.effect.IO
import cats.implicits._
import chameleon._

class Ws(operations: WsOperations) {
  def sendClient(implicit serializer: Serializer[ServerMessage[Unit, SubscriptionEvent, Unit], StringSerdes]) = Client.contra[StringSerdes, Kleisli[IO, *, Unit]](new WebsocketTransport(operations))
}

private trait WsOperations {
  def sendToSubscription(subscriptionKey: String, data: SubscriptionEvent)(implicit serializer: Serializer[ServerMessage[Unit, SubscriptionEvent, Unit], StringSerdes]): IO[Unit]
}

private class WsOperationsAWS(eventsSnsTopic: String) extends WsOperations {

  private implicit val cs  = IO.contextShift(scala.concurrent.ExecutionContext.global)
  private val snsClient = new SNS()

  def sendToSubscription(subscriptionKey: String, data: SubscriptionEvent)(implicit serializer: Serializer[ServerMessage[Unit, SubscriptionEvent, Unit], StringSerdes]): IO[Unit] = {
    val serializedData = serializer.serialize(Notification[SubscriptionEvent](data))
    val params = PublishInput(
      Message = serializedData.value,
      TopicArn = eventsSnsTopic
    )

    IO.fromFuture(IO(snsClient.publishFuture(params)))
      .flatTap(r => IO(js.Dynamic.global.console.log("PUBLISHED", r)))
      .void
  }
}

private class WsOperationsDev(send: (String, String) => Unit) extends WsOperations {

  def sendToSubscription(subscriptionKey: String, data: SubscriptionEvent)(implicit serializer: Serializer[ServerMessage[Unit, SubscriptionEvent, Unit], StringSerdes]): IO[Unit] =
    IO(send(subscriptionKey, serializer.serialize(Notification[SubscriptionEvent](data)).value))
}

private class WebsocketTransport(operations: WsOperations)(implicit serializer: Serializer[ServerMessage[Unit, SubscriptionEvent, Unit], StringSerdes]) extends RequestTransport[StringSerdes, Kleisli[IO, *, Unit]] {

  def apply(request: Request[StringSerdes]): Kleisli[IO, StringSerdes, Unit] = Kleisli { body =>
    val subscriptionKey = s"${request.path.mkString("/")}/${request.payload.value}"
    operations.sendToSubscription(subscriptionKey, SubscriptionEvent(subscriptionKey, body))
  }
}
