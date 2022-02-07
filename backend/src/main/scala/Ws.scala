package funstack.backend

import funstack.core.{SubscriptionEvent, StringSerdes}
import facade.amazonaws.AWSConfig
import facade.amazonaws.services.dynamodb._
import facade.amazonaws.services.apigatewaymanagementapi._
import scala.scalajs.js
import sloth._

import mycelium.core.message.{Notification, ServerMessage}

import cats.data.Kleisli
import cats.effect.IO
import cats.implicits._
import chameleon._

import scala.scalajs.js.JSConverters._

class Ws(operations: WsOperations) {
  def sendClient(implicit serializer: Serializer[ServerMessage[Unit, SubscriptionEvent, Unit], StringSerdes]) = Client.contra[StringSerdes, Kleisli[IO, *, Unit]](new WebsocketTransport(operations))
}

private trait WsOperations {
  def sendToSubscription(subscriptionKey: String, data: SubscriptionEvent)(implicit serializer: Serializer[ServerMessage[Unit, SubscriptionEvent, Unit], StringSerdes]): IO[Unit]
}

private class WsOperationsAWS(subscriptionsTable: String, apiGatewayEndpoint: String) extends WsOperations {

  private implicit val cs  = IO.contextShift(scala.concurrent.ExecutionContext.global)
  private val dynamoClient = new DynamoDB()
  private val apiClient    = new ApiGatewayManagementApi(AWSConfig(endpoint = apiGatewayEndpoint))

  case class QueryResult[T](nextToken: Option[Key], result: T)

  def getConnectionIdsOfSubscription(subscriptionKey: String, nextToken: Option[Key]): IO[QueryResult[List[String]]] = IO
    .fromFuture(
      IO(
        dynamoClient.queryFuture(
          QueryInput(
            TableName = subscriptionsTable,
            ExpressionAttributeValues = js.Dictionary(":subscription_key" -> AttributeValue(S = subscriptionKey)),
            KeyConditionExpression = "subscription_key = :subscription_key",
            ProjectionExpression = "connection_id",
            ExclusiveStartKey = nextToken.orUndefined,
          ),
        ),
      ),
    ).map { result =>
      val items = result.Items.fold(List.empty[String])(_.flatMap(_.get("connection_id").flatMap(_.S.toOption)).toList)
      QueryResult(nextToken = result.LastEvaluatedKey.toOption, result = items)
    }

  def sendToConnection(connectionId: String, data: StringSerdes): IO[Unit] = IO.fromFuture(
    IO(apiClient.postToConnectionFuture(PostToConnectionRequest(ConnectionId = connectionId, data.value)))
  ).attempt.void //ignoring erros. Should we cleanup old ones? GoneException?

  //TODO: we currently just do a sequential run over the data here. we should parallelize and maybe even distribute the batches
  def sendToSubscription(subscriptionKey: String, data: SubscriptionEvent)(implicit serializer: Serializer[ServerMessage[Unit, SubscriptionEvent, Unit], StringSerdes]): IO[Unit] = {
    val serializedData = serializer.serialize(Notification[SubscriptionEvent](data))
    def inner(nextToken: Option[Key]): IO[Unit] = getConnectionIdsOfSubscription(subscriptionKey, nextToken).flatMap { queryResult =>
      queryResult.result.traverse(sendToConnection(_, serializedData)).void *> queryResult.nextToken.traverse(token => inner(Some(token))).void
    }

    inner(None)
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
