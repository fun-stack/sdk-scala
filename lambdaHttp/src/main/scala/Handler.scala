package funstack.lambda.http

import net.exoego.facade.aws_lambda._
import cats.effect.{IO, Effect}
import cats.implicits._
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.interceptor.RequestResult

import scala.scalajs.js

object Handler {
  type FunctionType = js.Function2[APIGatewayProxyEventV2, Context, js.Promise[APIGatewayProxyStructuredResultV2]]

  def handle[F[_]: Effect](
      endpoints: List[ServerEndpoint[_, F]],
  ): FunctionType = { (event, context) =>
    // println(js.JSON.stringify(event))
    // println(js.JSON.stringify(context))

    val interpreter = LambdaServerInterpreter[F](event)

    val run = interpreter(new LambdaServerRequest(event), endpoints).map {
      case RequestResult.Response(response) =>
        println(response)
        response.body.getOrElse(APIGatewayProxyStructuredResultV2(statusCode = 404))
      case RequestResult.Failure(errors) =>
        println(s"No response, errors: ${errors.mkString(", ")}")
        APIGatewayProxyStructuredResultV2(statusCode = 404)
    }

    new js.Promise[APIGatewayProxyStructuredResultV2]((resolve, reject) =>
      Effect[F]
        .runAsync(run)(_.fold(error => IO(reject(error)).void, value => IO(resolve(value)).void))
        .unsafeRunSync(),
    )
  }
}
