package funstack.backend

import funstack.core.StringSerdes
import facade.amazonaws.AWSConfig
import facade.amazonaws.services.dynamodb._
import facade.amazonaws.services.apigatewaymanagementapi._
import scala.scalajs.js

import mycelium.core.message.{Notification, ServerMessage}

import cats.effect.IO
import cats.implicits._
import chameleon._
import java.time.Instant

class Ws[Event](tableName: String, apiGatewayEndpoint: String) {

  private implicit val cs  = IO.contextShift(scala.concurrent.ExecutionContext.global)
  private val dynamoClient = new DynamoDB()
  private val apiClient    = new ApiGatewayManagementApi(AWSConfig(endpoint = apiGatewayEndpoint))


  object sender {
    import cats.data.Kleisli
    import sloth._

    case class SendRequest[+T](connectionId: String, body: T)
    type IOKleisli[-T] = Kleisli[IO, SendRequest[T], Unit]

    implicit def ioKleisliClientContraHandler: ClientContraHandler[IOKleisli] = new ClientContraHandler[IOKleisli] {
      override def raiseFailure[B](failure: ClientFailure): IOKleisli[B] = Kleisli.liftF(IO.raiseError(failure.toException))
      override def contramap[A,B](fa: IOKleisli[A])(f: B => A): IOKleisli[B] = Kleisli(b => fa(b.copy[A](body = f(b.body))))
    }

    val send = new RequestTransport[String, IOKleisli] {
      def apply(request: Request[String]): IOKleisli[String] = Kleisli { sendRequest =>
        val subscriptionKey = s"${request.path.mkString("/")}/${request.payload}"
        ???
      }
    }

    val register = new RequestTransport[String, IOKleisli] {
      def apply(request: Request[String]): IOKleisli[String] = Kleisli { sendRequest =>
        val subscriptionKey = s"${request.path.mkString("/")}/${request.payload}"
        storeSubscription(connectionId = sendRequest.connectionId, subscriptionKey = subscriptionKey)
      }
    }

    val unregister = new RequestTransport[String, IOKleisli] {
      def apply(request: Request[String]): IOKleisli[String] = Kleisli { sendRequest =>
        val subscriptionKey = s"${request.path.mkString("/")}/${request.payload}"
        deleteSubscription(connectionId = sendRequest.connectionId, subscriptionKey = subscriptionKey)
      }
    }

    def registerClient = Client.contra[String, IOKleisli](register)
    def unregisterClient = Client.contra[String, IOKleisli](unregister)
    def senderClient = Client.contra[String, IOKleisli](send)

    trait MyApi[F[_]] {
      def test(id: String): F[Int]
    }


    implicit def serializer[T] = new Serializer[T, String] { def serialize(x: T) = x.toString }
      val sendApi = senderClient.wire[MyApi[IOKleisli]]
      val registerApi = registerClient.wire[MyApi[IOKleisli]]
      val unregisterApi = unregisterClient.wire[MyApi[IOKleisli]]

      sendApi.test("hallo").apply(SendRequest("conn-1", 2)).unsafeRunSync()
      registerApi.test("hallo").apply(SendRequest("conn-1", -1)).unsafeRunSync()
      unregisterApi.test("hallo").apply(SendRequest("conn-1", -1)).unsafeRunSync()

      // val api = client.wire[MyApi[IOKleisli]]

      // api.test("hallo").send(ConnectionRequest("conn-1", 2)).unsafeRunSync()
      // api.test("hallo").register(ConnectionRequest("conn-1", -1)).unsafeRunSync()
      // api.test("hallo").unregister(ConnectionRequest("conn-1", -1)).unsafeRunSync()

  //   def client[PickleType](implicit
  //       serializer: Serializer[ClientMessage[PickleType], StringSerdes],
  //       deserializer: Deserializer[ServerMessage[PickleType, Event, Unit], StringSerdes],
  //   ) = clientF[PickleType, IO]

    // def clientF[PickleType, F[_]: Async]
    // (implicit
    //     serializer: Serializer[ClientMessage[PickleType], StringSerdes],
    //     deserializer: Deserializer[ServerMessage[PickleType, Event, Unit], StringSerdes],
    // )
    // = Client[PickleType, F](WebsocketTransport[Event, Unit, PickleType, F](ws, auth, eventsSubject))

  //   def clientFuture[PickleType](implicit
  //       serializer: Serializer[ClientMessage[PickleType], StringSerdes],
  //       deserializer: Deserializer[ServerMessage[PickleType, Event, Unit], StringSerdes],
  //   ) = Client[PickleType, Future](WebsocketTransport[Event, Unit, PickleType, IO](ws, auth, eventsSubject).map(_.unsafeToFuture()))
  }

  // object registrar {
  //   def client[PickleType](implicit
  //       serializer: Serializer[ClientMessage[PickleType], StringSerdes],
  //       deserializer: Deserializer[ServerMessage[PickleType, Event, Unit], StringSerdes],
  //   ) = clientF[PickleType, IO]

  //   def clientF[PickleType, F[_]: Async](implicit
  //       serializer: Serializer[ClientMessage[PickleType], StringSerdes],
  //       deserializer: Deserializer[ServerMessage[PickleType, Event, Unit], StringSerdes],
  //   ) = Client[PickleType, F](WebsocketTransport[Event, Unit, PickleType, F](ws, auth, eventsSubject))

  //   def clientFuture[PickleType](implicit
  //       serializer: Serializer[ClientMessage[PickleType], StringSerdes],
  //       deserializer: Deserializer[ServerMessage[PickleType, Event, Unit], StringSerdes],
  //   ) = Client[PickleType, Future](WebsocketTransport[Event, Unit, PickleType, IO](ws, auth, eventsSubject).map(_.unsafeToFuture()))
  // }

  def getConnectionIdsOfSubscription(subscriptionKey: String): IO[List[String]] = IO
    .fromFuture(
      IO(
        dynamoClient.queryFuture(
          QueryInput(
            TableName = "TODO",
            ExpressionAttributeValues = js.Dictionary(":subscription_key" -> AttributeValue(S = subscriptionKey)),
            KeyConditionExpression = "subscription_key = :subscription_key",
            ProjectionExpression = "connection_id",
          ),
        ),
      ),
    ).map(_.Items.fold(List.empty[String])(_.toList.flatMap(_.get("connection_id").flatMap(_.S.toOption))))

  def deleteSubscription(connectionId: String, subscriptionKey: String): IO[Unit] = IO
    .fromFuture(
      IO(
        // do not fail if already deleted?
        dynamoClient.deleteItemFuture(
          DeleteItemInput(
            TableName = "TODO",
            Key = js.Dictionary(
              "connection_id" -> AttributeValue(S = connectionId),
              "subscription_key" -> AttributeValue(S = subscriptionKey),
            )
          )
        ),
      ),
    ).void

  def storeSubscription(connectionId: String, subscriptionKey: String): IO[Unit] = IO
    .fromFuture(
      IO(
        // do not fail if exists. or create another?
        dynamoClient.putItemFuture(
          PutItemInput(
            TableName = "TODO",
            Item = js.Dictionary(
              "connection_id" -> AttributeValue(S = connectionId),
              "subscription_key" -> AttributeValue(S = subscriptionKey),
              // TODO: a connection is valid for maximum 2 hours (aws).
              // In the best case this table would cleaned up automatically when a client disconnects, just like the connections table.
              // But we need to write our own lambda for that, we can only delete one item in the integration.
              // At least the subscriptions are cleaned up, when timed out, or when explicitly cleared, but not on disconnect.
              "ttl" -> AttributeValue(N = s"${Instant.now().plusSeconds(2 * 60 * 60).getEpochSecond}"),
            ),
          )
        ),
      ),
    ).void

  def getConnectionIdsOfUser(userId: String): IO[List[String]] = IO
    .fromFuture(
      IO(
        dynamoClient.queryFuture(
          QueryInput(
            TableName = tableName,
            IndexName = "user_id_index",
            ExpressionAttributeValues = js.Dictionary(":user_id" -> AttributeValue(S = userId)),
            KeyConditionExpression = "user_id = :user_id",
            ProjectionExpression = "connection_id",
          ),
        ),
      ),
    ).map(_.Items.fold(List.empty[String])(_.toList.flatMap(_.get("connection_id").flatMap(_.S.toOption))))

  def sendToConnection(connectionId: String, data: Event)(implicit serializer: Serializer[ServerMessage[Unit, Event, Unit], StringSerdes]): IO[Unit] = IO.fromFuture(
      IO(
        apiClient.postToConnectionFuture(
          PostToConnectionRequest(
            ConnectionId = connectionId,
            serializer.serialize(Notification[Event](data)).value
          ),
        ),
      ),
    ).void

  def sendToUser(userId: String, data: Event)(implicit serializer: Serializer[ServerMessage[Unit, Event, Unit], StringSerdes]): IO[Unit] =
    getConnectionIdsOfUser(userId).flatMap { connectionIds =>
      connectionIds.traverse(sendToConnection(_, data)).void
    }

  def sendToSubscription(subscriptionKey: String, data: Event)(implicit serializer: Serializer[ServerMessage[Unit, Event, Unit], StringSerdes]): IO[Unit] =
    getConnectionIdsOfSubscription(subscriptionKey).flatMap { connectionIds =>
      connectionIds.traverse(sendToConnection(_, data)).void
    }
}
