package funstack.lambda.http.tapir

import net.exoego.facade.aws_lambda._
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.openapi.Info
import sttp.tapir.docs.openapi.{OpenAPIDocsInterpreter, OpenAPIDocsOptions}
import io.circe.syntax._
import sttp.tapir.redoc.Redoc
import sttp.tapir.openapi.circe._

import scala.scalajs.js

object DocServer {
    private val title = "API"
    private val version = "latest"
    private val prefix = "docs"

    def result(body: String, contentType: String): APIGatewayProxyStructuredResultV2 = APIGatewayProxyStructuredResultV2(
      body = body,
      headers = js.Dictionary("Content-Type" -> contentType).asInstanceOf[HeadersBDS]
    )

    def serve[F[_]](path: List[String], endpoints: List[ServerEndpoint[_, F]]): Option[APIGatewayProxyStructuredResultV2] = path match {
      case List(`prefix`) | List(`prefix`, "index.html") =>
        val html = Redoc.redocHtml(title, s"/$prefix/openapi.json")
        Some(result(html, "text/html"))

      case List(`prefix`, "openapi.json") =>
        val openapi = OpenAPIDocsInterpreter(OpenAPIDocsOptions.default)
          .serverEndpointsToOpenAPI[F](endpoints, Info(title = title, version = version))
        val json = openapi.asJson.spaces2SortKeys
        Some(result(json, "application/json"))

      case _ => None
    }
}
