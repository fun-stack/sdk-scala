package funstack.lambda.core

import net.exoego.facade.aws_lambda._
import scala.scalajs.js
import scala.scalajs.js.|

object HandlerFunction {

  type Type[T] = js.Function2[T, Context, js.Promise[APIGatewayProxyStructuredResultV2]]

  def combine(func1: Type[_], func2: Type[_]): Type[_] = { (event, context) =>
    val r: js.Promise[APIGatewayProxyStructuredResultV2] = func1(event, context)

    r.`then`[APIGatewayProxyStructuredResultV2]({ result =>
      if (result.statusCode.exists(_ == 404)) func2(event, context)
      else result
    }: js.Function1[APIGatewayProxyStructuredResultV2, APIGatewayProxyStructuredResultV2 | js.Thenable[APIGatewayProxyStructuredResultV2]])
  }
}
