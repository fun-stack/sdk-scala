package funstack.backend

import cats.data.Kleisli
import cats.effect.IO
import facade.amazonaws.services.sns._
import funstack.core.{CanSerialize, SubscriptionEvent}
import funstack.ws.core.ServerMessageSerdes
import sloth._

import scala.concurrent.Future
import scala.scalajs.js

class Ws(operations: WsOperations) {
  def sendTransport[T: CanSerialize]               = new WsTransport[T](operations)
  def sendTransportFunction[T: CanSerialize]       = new WsTransportFunction[T](operations)
  def sendTransportFuture[T: CanSerialize]         = new WsTransportFuture[T](operations)
  def sendTransportFunctionFuture[T: CanSerialize] = new WsTransportFunctionFuture[T](operations)
}

private trait WsOperations {
  def sendToSubscription(subscriptionKey: String, data: SubscriptionEvent): IO[Unit]
}

private class WsOperationsAWS(eventsSnsTopic: String) extends WsOperations {

  private val snsClient = new SNS()

  def sendToSubscription(subscriptionKey: String, data: SubscriptionEvent): IO[Unit] = {
    val serializedData = ServerMessageSerdes.serializer.notification(data)
    val params         = PublishInput(
      Message = serializedData,
      MessageAttributes = js.Dictionary("subscription_key" -> MessageAttributeValue(DataType = "String", StringValue = subscriptionKey)),
      TopicArn = eventsSnsTopic,
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

  private val inner = new WsTransportFunction[T](operations)

  def apply(request: Request[T]): Kleisli[IO, T, Unit] = Kleisli(inner(request))
}

class WsTransportFunction[T: CanSerialize](operations: WsOperations) extends RequestTransport[T, * => IO[Unit]] {

  def apply(request: Request[T]): T => IO[Unit] = { body =>
    val subscriptionKey = s"${request.path.mkString("/")}/${js.Dynamic.global.escape(CanSerialize[T].serialize(request.payload))}"
    operations.sendToSubscription(subscriptionKey, SubscriptionEvent(subscriptionKey, CanSerialize[T].serialize(body)))
  }
}

class WsTransportFuture[T: CanSerialize](operations: WsOperations)         extends RequestTransport[T, Kleisli[Future, *, Unit]] {
  import cats.effect.unsafe.implicits.global

  private val inner = new WsTransport[T](operations)

  def apply(request: Request[T]): Kleisli[Future, T, Unit] = inner(request).mapF(_.unsafeToFuture())
}
class WsTransportFunctionFuture[T: CanSerialize](operations: WsOperations) extends RequestTransport[T, * => Future[Unit]]        {
  import cats.effect.unsafe.implicits.global

  private val inner = new WsTransportFunction[T](operations)

  def apply(request: Request[T]): T => Future[Unit] = t => inner(request)(t).unsafeToFuture()
}
