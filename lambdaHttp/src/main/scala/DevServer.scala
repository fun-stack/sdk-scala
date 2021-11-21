package funstack.lambda.http

import funstack.lambda.typings
import typings.node.httpMod.createServer
import typings.node.httpMod.IncomingMessage
import typings.node.httpMod.ServerResponse
import typings.node.{Buffer => JsBuffer}
import net.exoego.facade.aws_lambda.APIGatewayProxyEventV2
import net.exoego.facade.aws_lambda
import org.scalajs.dom.console
import collection.mutable
import scala.scalajs.js
import scala.util.{Failure, Success}
import java.net.URI
import js.JSConverters._

object DevServer {
  private implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global

  def start(lambdaHandler: Handler.FunctionType, port: Int) = {
    val requestListener = { (req: IncomingMessage, res: ServerResponse) =>

      val body = StringBuilder.newBuilder
      req.on(
        "data",
        (chunk) => {
          val buffer = chunk.asInstanceOf[JsBuffer];
          body ++= buffer.toString()
          ()
        },
      )
      req.on(
        "end",
        (_) => {
          val bodyStr                       = body.result()
          val (gatewayEvent, lambdaContext) = transform(req, bodyStr)

          println("-" * 20)
          lambdaHandler(gatewayEvent, lambdaContext).toFuture.onComplete {
            case Success(result) =>
              result.statusCode.foreach(res.statusCode = _)
              res.end(result.body)
            case Failure(error)  =>
              res.statusCode = 500 // internal server error
              error.printStackTrace()
              res.end()
          }
        },
      )

      ()
    }

    val server = createServer(requestListener)
    server.listen(port)
    server
  }






  def transform(req: IncomingMessage, body: String): (APIGatewayProxyEventV2, aws_lambda.Context) = {

    val url             = new URI(req.url.getOrElse("https://"))
    val queryParameters = Option(url.getQuery)
      .fold(Map.empty[String, String])(
        _.split("&|=")
          .grouped(2)
          .map(a => (a(0) -> a(1)))
          .toMap,
      )
    // https://docs.aws.amazon.com/lambda/latest/dg/services-apigateway.html#apigateway-example-event
    // example json: https://github.com/awsdocs/aws-lambda-developer-guide/blob/main/sample-apps/nodejs-apig/event-v2.json
    // more examples: https://github.com/search?l=JSON&q=pathparameters+requestContext+isbase64encoded&type=Code
    val routeKey        = "ANY /nodejs-apig-function-1G3XMPLZXVXYI"
    val now             = new js.Date()
    val host            = Option(url.getHost()).getOrElse("") // TODO: includes port number, but it shouldn't
    val path            = s"/latest${url.getPath()}"          //TODO: why latest?
    val gateWayEvent    = APIGatewayProxyEventV2(
      version = "2.0",
      routeKey = routeKey,
      rawPath = path,
      rawQueryString = Option(url.getQuery()).getOrElse(""),
      cookies = req.headers.cookie.fold(js.Array[String]())(_.split(";").toJSArray),
      // TODO: multi valued headers
      // TODO: include cookies in headers?
      headers = req.headers.asInstanceOf[js.Dictionary[String]],
      requestContext = APIGatewayProxyEventV2.RequestContext(
        accountId = "123456789012",
        apiId = "r3pmxmplak",
        authorizer = js.undefined,      // TODO: js.UndefOr[RequestContext.Authorizer]
        domainName = host,
        domainPrefix = host.split(".").headOption.getOrElse(url.getHost()),
        http = APIGatewayProxyEventV2.RequestContext.Http(
          method = req.method.getOrElse(""),
          path = path,
          protocol = "HTTP/1.1",
          sourceIp = "127.0.0.1",
          userAgent = req.headers.`user-agent`.getOrElse(""),
        ),                              // RequestContext.Http
        requestId = "WYAk6i4ZjoEEJSQ=", // TODO: random
        routeKey = routeKey,
        stage = "$default",
        time =
          now.toISOString(),            //TODO: ISO 8601 maybe not correct. Examples have "21/Nov/2020:20:39:08 +0000" which is a different format,
        timeEpoch = now.getUTCMilliseconds(),
      ),
      isBase64Encoded = false,
      body = body,
      pathParameters = js.undefined, // TODO: js.Dictionary for /{id}/ in URL
      queryStringParameters = queryParameters.toJSDictionary, //js.Dictionary[String](),
    )

    val lambdaContext = js.Dynamic
      .literal(
        callbackWaitsForEmptyEventLoop = true,
        functionName = "function",
        functionVersion = "$LATEST",
        invokedFunctionArn = "arn:aws:lambda:ap-southeast-2:[AWS_ACCOUNT_ID]:function:restapi",
        memoryLimitInMB = "128",
        awsRequestId = "1d9ccf1c-0f09-427e-b2f8-ffc961d25904",
        logGroupName = "",
        logStreamName = s"/aws/lambda/function",
        // var identity: js.UndefOr[aws_lambda.CognitoIdentity]    = js.undefined
        // var clientContext: js.UndefOr[aws_lambda.ClientContext] = js.undefined
      )
      .asInstanceOf[aws_lambda.Context]

    (gateWayEvent, lambdaContext)
  }
}

