package funstack.backend

import funstack.ws.core.{ServerMessageSerdes, ClientMessageSerdes}
import funstack.core.{SubscriptionEvent, CanSerialize}
import facade.amazonaws.services.sns._
import scala.scalajs.js
import sloth._

import mycelium.core.message.{Notification, ServerMessage}

import cats.data.Kleisli
import cats.effect.IO
import cats.implicits._
import chameleon._

class Ws(operations: WsOperations) {
  def sendTransport[T: CanSerialize] = new WsTransport[T](operations)
}

private trait WsOperations {
  def sendToSubscription(subscriptionKey: String, data: SubscriptionEvent): IO[Unit]
}

private class WsOperationsAWS(eventsSnsTopic: String) extends WsOperations {

  private implicit val cs  = IO.contextShift(scala.concurrent.ExecutionContext.global)
  private val snsClient = new SNS()

  def sendToSubscription(subscriptionKey: String, data: SubscriptionEvent): IO[Unit] = {
    val serializedData = ServerMessageSerdes.serializer.notification(data)
    val params = PublishInput(
      Message = serializedData,
      MessageAttributes = js.Dictionary("subscription_key" -> MessageAttributeValue(DataType = "String", StringValue = subscriptionKey)),
      TopicArn = eventsSnsTopic
    )

    IO.fromFuture(IO(snsClient.publishFuture(params)))
      .flatTap(r => IO(js.Dynamic.global.console.log("PUBLISHED", r)))
      .void
  }
}

private class WsOperationsDev(send: (String, String) => Unit) extends WsOperations {

  def sendToSubscription(subscriptionKey: String, data: SubscriptionEvent): IO[Unit] = {
    val serializedData = ServerMessageSerdes.serializer.notification(data)
    IO(send(subscriptionKey, serializedData))
  }
}

class WsTransport[T: CanSerialize](operations: WsOperations) extends RequestTransport[T, Kleisli[IO, *, Unit]] {

  def apply(request: Request[T]): Kleisli[IO, T, Unit] = Kleisli { body =>
    val subscriptionKey = s"${request.path.mkString("/")}/${CanSerialize[T].serialize(request.payload)}"
    operations.sendToSubscription(subscriptionKey, SubscriptionEvent(subscriptionKey, CanSerialize[T].serialize(body)))
  }
}
