package funstack.backend

import facade.amazonaws.services.dynamodb._
import facade.amazonaws.services.apigatewaymanagementapi._
import scala.scalajs.js

import mycelium.core.message.{Notification, ServerMessage}

import cats.effect.IO
import cats.implicits._
import chameleon._

class Ws[Event](table: String) {
  private implicit val cs  = IO.contextShift(scala.concurrent.ExecutionContext.global)
  private val dynamoClient = new DynamoDB()
  private val apiClient    = new ApiGatewayManagementApi()

  def getConnectionIdsOfUser(userId: String): IO[List[String]] = IO
    .fromFuture(
      IO(
        dynamoClient.queryFuture(
          QueryInput(
            TableName = table,
            ExpressionAttributeValues = js.Dictionary(":user_id" -> AttributeValue(S = userId)),
            KeyConditionExpression = "user_id = :user_id",
            ProjectionExpression = "connection_id",
          ),
        ),
      ),
    )
    .map(_.Items.fold(List.empty[String])(_.toList.flatMap(_.get("connection_id").flatMap(_.S.toOption))))

  def sendToConnection(connectionId: String, data: Event)(implicit
      serializer: Serializer[ServerMessage[Nothing, Event, Nothing], WsPickleType],
  ): IO[Unit] =
    IO.fromFuture(
      IO(
        apiClient.postToConnectionFuture(
          PostToConnectionRequest(
            ConnectionId = connectionId,
            serializer.serialize(Notification[Event](data)).dataValue,
          ),
        ),
      ),
    ).void

  def sendToUser(userId: String, data: Event)(implicit serializer: Serializer[ServerMessage[Nothing, Event, Nothing], WsPickleType]): IO[Unit] =
    getConnectionIdsOfUser(userId).flatMap { connectionIds =>
      connectionIds.traverse(sendToConnection(_, data)).void
    }
}
