package funstack.backend

import facade.amazonaws.AWSConfig
import facade.amazonaws.services.dynamodb._
import facade.amazonaws.services.apigatewaymanagementapi._
import scala.scalajs.js

import mycelium.core.message.{Notification, ServerMessage}

import cats.effect.IO
import cats.implicits._
import chameleon._

class Ws[Event](tableName: String, apiGatewayEndpoint: String) {
  private implicit val cs  = IO.contextShift(scala.concurrent.ExecutionContext.global)
  private val dynamoClient = new DynamoDB()
  private val apiClient    = new ApiGatewayManagementApi(AWSConfig(endpoint = apiGatewayEndpoint))

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

  def sendToConnection(connectionId: String, data: Event)(implicit
      serializer: Serializer[ServerMessage[String, Event, String], String],
  ): IO[Unit] = IO.fromFuture(
      IO(
        apiClient.postToConnectionFuture(
          PostToConnectionRequest(
            ConnectionId = connectionId,
            serializer.serialize(Notification[Event](data))
          ),
        ),
      ),
    ).void

  def sendToUser(userId: String, data: Event)(implicit serializer: Serializer[ServerMessage[String, Event, String], String]): IO[Unit] =
    getConnectionIdsOfUser(userId).flatMap { connectionIds =>
      connectionIds.traverse(sendToConnection(_, data)).void
    }
}
