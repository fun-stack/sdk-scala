package funstack.lambda.http

import net.exoego.facade.aws_lambda._
import cats.effect.{IO, Effect}
import cats.data.Kleisli
import cats.implicits._
import sttp.tapir.server.ServerEndpoint

import scala.scalajs.js
import scala.concurrent.Future
import scala.scalajs.js.JSConverters._

object Handler {
  import scala.concurrent.ExecutionContext.Implicits.global

  type FunctionType = js.Function2[APIGatewayProxyEventV2, Context, js.Promise[APIGatewayProxyStructuredResultV2]]

  def handle[F[_]: Effect](
      endpoints: List[ServerEndpoint[_, _, _, _, F]],
  ): FunctionType = { (event, context) =>
    println(js.JSON.stringify(event))
    println(js.JSON.stringify(context))

    val interpreter = LambdaServerInterpreter[F](event)

    val run = interpreter(new LambdaServerRequest(event), endpoints).map {
      case Some(response) =>
        println(response)
        response.body.getOrElse(APIGatewayProxyStructuredResultV2(statusCode = 404))
      case None =>
        println("No Response")
        APIGatewayProxyStructuredResultV2(statusCode = 404)
    }

    new js.Promise[APIGatewayProxyStructuredResultV2]((resolve, reject) =>
      Effect[F]
        .runAsync(run)(_.fold(error => IO(reject(error)), value => IO(resolve(value))))
        .unsafeRunSync(),
    )
  }
}
