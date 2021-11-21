package funstack.lambda.ws

import scala.scalajs.js

import net.exoego.facade.aws_lambda
import funstack.lambda.ws.facades
import facades.ws.mod.WebSocketServer
import facades.ws.mod.ServerOptions
import facades.ws.wsStrings
import facades.jwtDecode.mod.{default => jwt_decode}
import facades.jwtDecode.mod.JwtPayload
import scala.util.{Success, Failure}

object DevServer {
  implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global

  def transform(body: String, accessToken: String): (APIGatewayWSEvent, aws_lambda.Context) = {

    val decodedToken = jwt_decode[JwtPayload](accessToken)

    val authorizer =
      if (accessToken == "anon")
        js.Dynamic.literal(
          principalId = "user",
        )
      else {
        js.Object.assign(
          js.Dynamic
            .literal(
              principalId = "user",
            )
            .asInstanceOf[js.Object],
          decodedToken,
        )
      }

    val randomRequestId = util.Random.alphanumeric.take(20).mkString
    val randomMessageId = util.Random.alphanumeric.take(20).mkString
    val now             = new js.Date()

    val event = js.Dynamic
      .literal(
        requestContext = js.Dynamic.literal(
          routeKey = "$default",
          authorizer = authorizer,
          messageId = randomMessageId,
          eventType = "MESSAGE",
          extendedRequestId = randomRequestId,
          requestTime = now.toISOString(),            //TODO: ISO 8601 maybe not correct. Examples have "21/Nov/2020:20:39:08 +0000" which is a different format,
          messageDirection = "IN",
          stage = "latest",
          connectedAt = now.getUTCMilliseconds(),
          requestTimeEpoch = now.getUTCMilliseconds(),
          identity = js.Dynamic.literal(
            userAgent = "Mozilla/5.0 (X11; Linux x86_64; rv =94.0) Gecko/20100101 Firefox/94.0",
            sourceIp = "127.0.0.1",
          ),
          requestId = randomRequestId,
          domainName = "localhost",
          connectionId = "I6BtqeyAliACH_Q=", //TODO: random per connection
          apiId = "jck5a4ero8",
        ),
        body = body,
        isBase64Encoded = false,
      )
      .asInstanceOf[APIGatewayWSEvent]

    val lambdaContext = js.Dynamic
      .literal(
        callbackWaitsForEmptyEventLoop = true,
        functionVersion = "$LATEST",
        functionName = "function-name",
        memoryLimitInMB = "256",
        logGroupName = "/aws/lambda/function-name",
        logStreamName = "2021/11/16/[$LATEST]fd71a67c7c5a4c68ad4d591119ebfdae",
        invokedFunctionArn = "arn:aws:lambda:eu-central-1:123456789012:function:function-name",
        awsRequestId = "3ddc97fc-c7f9-45fc-856b-c3efdad9c544",
      )
      .asInstanceOf[aws_lambda.Context]

    (event, lambdaContext)
  }

  def start(lambdaHandler: Handler.FunctionType, port: Int): Unit = {
    val wss = new WebSocketServer(ServerOptions().setPort(port.toDouble))
    wss.on_connection(
      wsStrings.connection,
      { (_, ws, msg) =>
        println("new connection")
        val token = msg.url.get.split("=")(1)
        ws.on_message(
          wsStrings.message,
          { (_, data, _) =>
            val body = data.toString
            val (event, context) = transform(body, token)
            lambdaHandler(event, context).toFuture.onComplete {
              case Success(result) =>
                ws.send(result.body)
              case Failure(error)  =>
                error.printStackTrace()
            }
          },
        )
      },
    )
  }
}
