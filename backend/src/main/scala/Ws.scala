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
import java.time.Instant

import scala.scalajs.js.JSConverters._

class Ws(tableName: String, subscriptionsTable: String, apiGatewayEndpoint: String)(implicit
      serializer: Serializer[ServerMessage[Unit, SubscriptionEvent, Unit], StringSerdes],
      ) {

  private implicit val cs  = IO.contextShift(scala.concurrent.ExecutionContext.global)
  private val dynamoClient = new DynamoDB()
  private val apiClient    = new ApiGatewayManagementApi(AWSConfig(endpoint = apiGatewayEndpoint))

  val sendClient = Client.contra[StringSerdes, Kleisli[IO, *, Unit]](new WebsocketTransport(sendToSubscription(_,_)))

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

  def deleteSubscription(connectionId: String, subscriptionKey: String): IO[Unit] = IO
    .fromFuture(
      IO(
        // do not fail if already deleted?
        dynamoClient.deleteItemFuture(
          DeleteItemInput(
            TableName = subscriptionsTable,
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
            TableName = subscriptionsTable,
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

  def getConnectionIdsOfUser(userId: String, nextToken: Option[Key]): IO[QueryResult[List[String]]] = IO
    .fromFuture(
      IO(
        dynamoClient.queryFuture(
          QueryInput(
            TableName = tableName,
            IndexName = "user_id_index",
            ExpressionAttributeValues = js.Dictionary(":user_id" -> AttributeValue(S = userId)),
            KeyConditionExpression = "user_id = :user_id",
            ProjectionExpression = "connection_id",
            ExclusiveStartKey = nextToken.orUndefined,
          ),
        ),
      ),
    ).map { result =>
      val items = result.Items.fold(List.empty[String])(_.toList.flatMap(_.get("connection_id").flatMap(_.S.toOption)))
      QueryResult(nextToken = result.LastEvaluatedKey.toOption, result = items)
    }

  def sendToConnection(connectionId: String, data: SubscriptionEvent)(implicit serializer: Serializer[ServerMessage[Unit, SubscriptionEvent, Unit], StringSerdes]): IO[Unit] = IO.fromFuture(
      IO(
        apiClient.postToConnectionFuture(
          PostToConnectionRequest(
            ConnectionId = connectionId,
            serializer.serialize(Notification[SubscriptionEvent](data)).value
          ),
        )
      )
    ).attempt.void //ignoring erros. Should we cleanup old ones? GoneException?

  def sendToUser(userId: String, data: SubscriptionEvent)(implicit serializer: Serializer[ServerMessage[Unit, SubscriptionEvent, Unit], StringSerdes]): IO[Unit] = {
    def inner(nextToken: Option[Key]): IO[Unit] = getConnectionIdsOfUser(userId, nextToken).flatMap { queryResult =>
      queryResult.result.traverse(sendToConnection(_, data)).void *> queryResult.nextToken.traverse(token => inner(Some(token))).void
    }

    inner(None)
  }

  //TODO: we currently just do a sequential run over the data here. we should parallelize and maybe even distribute the batches
  def sendToSubscription(subscriptionKey: String, data: SubscriptionEvent)(implicit serializer: Serializer[ServerMessage[Unit, SubscriptionEvent, Unit], StringSerdes]): IO[Unit] = {
    def inner(nextToken: Option[Key]): IO[Unit] = getConnectionIdsOfSubscription(subscriptionKey, nextToken).flatMap { queryResult =>
      queryResult.result.traverse(sendToConnection(_, data)).void *> queryResult.nextToken.traverse(token => inner(Some(token))).void
    }

    inner(None)
  }
}

class WebsocketTransport(sendSubscription: (String, SubscriptionEvent) => IO[Unit]) extends RequestTransport[StringSerdes, Kleisli[IO, *, Unit]] {
  def apply(request: Request[StringSerdes]): Kleisli[IO, StringSerdes, Unit] = Kleisli { body =>
    val subscriptionKey = s"${request.path.mkString("/")}/${request.payload.value}"
    sendSubscription(subscriptionKey, SubscriptionEvent(subscriptionKey, body))
  }
}
