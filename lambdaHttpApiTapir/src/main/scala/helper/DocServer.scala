package funstack.lambda.http.api.tapir.helper

import io.circe.syntax._
import net.exoego.facade.aws_lambda._
import sttp.apispec.{ReferenceOr, SecurityRequirement, Tag}
import sttp.apispec.openapi.{Info, PathItem, Server}
import sttp.apispec.openapi.circe._
import sttp.tapir.docs.openapi.{OpenAPIDocsInterpreter, OpenAPIDocsOptions}
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.{EndpointInfoOps, EndpointMetaOps}

import scala.scalajs.js

case class DocInfo(
  info: Info,
  tags: List[Tag] = Nil,
  servers: List[Server] = Nil,
  webhooks: Option[Map[String, ReferenceOr[PathItem]]] = None,
  security: List[SecurityRequirement] = Nil,
  swaggerUIOptions: js.Object = js.Object(),
  filterEndpoints: EndpointInfoOps[_] with EndpointMetaOps => Boolean = _ => true,
)
object DocInfo {
  def default = DocInfo(title = "API", version = "latest")

  def apply(title: String, version: String): DocInfo = DocInfo(Info(title = title, version = version))
}

object DocServer {

  private def result(body: String, contentType: String): APIGatewayProxyStructuredResultV2 = APIGatewayProxyStructuredResultV2(
    statusCode = 200,
    body = body,
    headers = js.Dictionary("Content-Type" -> contentType).asInstanceOf[HeadersBDS],
  )

  def serve[F[_]](path: List[String], endpoints: List[ServerEndpoint[_, F]], docInfo: DocInfo): Option[APIGatewayProxyStructuredResultV2] = path match {
    case Nil | List("index.html") =>
      val html = SwaggerUI.html(docInfo.info.title, "openapi.json", docInfo.swaggerUIOptions)
      Some(result(html, "text/html"))

    case List("oauth2-redirect.html") =>
      val html = SwaggerUI.oauth2RedirectHtml(docInfo.info.title)
      Some(result(html, "text/html"))

    case List("openapi.json") =>
      val openapi = OpenAPIDocsInterpreter(OpenAPIDocsOptions.default)
        .serverEndpointsToOpenAPI[F](endpoints.filter(docInfo.filterEndpoints), docInfo.info)
        .copy(tags = docInfo.tags, webhooks = docInfo.webhooks, servers = docInfo.servers, security = docInfo.security)
      val json    = openapi.asJson.spaces2SortKeys
      Some(result(json, "application/json"))

    case _ => None
  }
}
