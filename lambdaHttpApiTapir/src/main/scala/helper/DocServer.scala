package funstack.lambda.http.api.tapir.helper

import io.circe.syntax._
import net.exoego.facade.aws_lambda._
import sttp.apispec.openapi.circe._
import sttp.tapir.docs.openapi.{OpenAPIDocsInterpreter, OpenAPIDocsOptions}
import sttp.tapir.redoc.Redoc
import sttp.tapir.server.ServerEndpoint

import scala.scalajs.js

object DocServer {
  private val title   = "API"
  private val version = "latest"

  def result(body: String, contentType: String): APIGatewayProxyStructuredResultV2 = APIGatewayProxyStructuredResultV2(
    statusCode = 200,
    body = body,
    headers = js.Dictionary("Content-Type" -> contentType).asInstanceOf[HeadersBDS],
  )

  def serve[F[_]](path: List[String], endpoints: List[ServerEndpoint[_, F]]): Option[APIGatewayProxyStructuredResultV2] = path match {
    case Nil | List("index.html") =>
      val html = Redoc.redocHtml(title, "openapi.json")
      Some(result(html, "text/html"))

    case List("openapi.json") =>
      val openapi = OpenAPIDocsInterpreter(OpenAPIDocsOptions.default)
        .serverEndpointsToOpenAPI[F](endpoints, title = title, version = version)
      val json    = openapi.asJson.spaces2SortKeys
      Some(result(json, "application/json"))

    case _ => None
  }
}
