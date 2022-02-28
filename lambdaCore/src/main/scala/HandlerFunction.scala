package funstack.lambda.core

import net.exoego.facade.aws_lambda._
import scala.scalajs.js

object HandlerFunction {

  type Type[T] = js.Function2[T, Context, js.Promise[APIGatewayProxyStructuredResultV2]]

  // This function is for combining two HTTP handlers into one, so you can
  // expose one export. This way you can have one lambda that hosts two
  // different APIs. This only works as desired when combining a http.Handler
  // with a http.tapir.Handler.
  def combine(funcA: Type[APIGatewayProxyEventV2], funcB: Type[APIGatewayProxyEventV2]): Type[APIGatewayProxyEventV2] = {
    def funcIsUnderscore(func: Type[APIGatewayProxyEventV2]): Boolean =
      func.asInstanceOf[js.Dynamic].__isUnderscore.asInstanceOf[js.UndefOr[Boolean]].getOrElse(false)

    val (underscoreFunc, defaultFunc) = (funcIsUnderscore(funcA), funcIsUnderscore(funcB)) match {
      case (true, true) => (Some(funcA), None)
      case (false, false) => (None, Some(funcA))
      case (true, false) => (Some(funcA), Some(funcB))
      case (false, true) => (Some(funcB), Some(funcA))
    }

    def notFoundResult = js.Promise.resolve[APIGatewayProxyStructuredResultV2](APIGatewayProxyStructuredResultV2(statusCode = 404))

    { (event, context) =>
      val path = event.requestContext.http.path.split("/").toList.drop(2)
      path match {
        case "_" :: _ => underscoreFunc.fold(notFoundResult)(_(event, context))
        case _        => defaultFunc.fold(notFoundResult)(_(event, context))
      }
    }
  }
}
