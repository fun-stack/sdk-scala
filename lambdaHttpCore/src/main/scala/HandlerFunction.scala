package funstack.lambda.http.core

import net.exoego.facade.aws_lambda._
import scala.scalajs.js
import scala.scalajs.js.|
import cats.effect.IO
import cats.data.Kleisli
import scala.concurrent.Future

object HandlerFunction {

  type Type = js.Function2[APIGatewayProxyEventV2, Context, js.Promise[APIGatewayProxyStructuredResultV2]]

  def combine(func1: Type, func2: Type): Type = { (event, context) =>
    val r: js.Promise[APIGatewayProxyStructuredResultV2] = func1(event, context)

    r.`then`[APIGatewayProxyStructuredResultV2]({ result =>
      if (result.statusCode.exists(_ == 404)) func2(event, context)
      else result
    }: js.Function1[APIGatewayProxyStructuredResultV2, APIGatewayProxyStructuredResultV2 | js.Thenable[APIGatewayProxyStructuredResultV2]])
  }
}
