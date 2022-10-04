package funstack.lambda.http.api.tapir.helper

import io.circe.syntax._
import net.exoego.facade.aws_lambda._
import sttp.apispec.openapi.Info
import sttp.apispec.openapi.circe._
import sttp.tapir.{EndpointMetaOps, EndpointInfoOps}
import sttp.tapir.docs.openapi.{OpenAPIDocsInterpreter, OpenAPIDocsOptions}
import sttp.tapir.server.ServerEndpoint

import scala.scalajs.js

case class DocInfo(info: Info, filterEndpoints: EndpointInfoOps[_] with EndpointMetaOps => Boolean)
object DocInfo {
  def default = DocInfo(title = "API", version = "latest")

  def apply(title: String, version: String): DocInfo = DocInfo(Info(title = title, version = version), filterEndpoints = _ => true)
}

object DocServer {

  private def result(body: String, contentType: String): APIGatewayProxyStructuredResultV2 = APIGatewayProxyStructuredResultV2(
    statusCode = 200,
    body = body,
    headers = js.Dictionary("Content-Type" -> contentType).asInstanceOf[HeadersBDS],
  )

  def serve[F[_]](path: List[String], endpoints: List[ServerEndpoint[_, F]], docInfo: DocInfo): Option[APIGatewayProxyStructuredResultV2] = path match {
    case Nil | List("index.html") =>
      val html = SwaggerUI.html(docInfo.info.title, "openapi.json")
      Some(result(html, "text/html"))

    case List("oauth2-redirect.html") =>
      val html = SwaggerUI.oauth2RedirectHtml(docInfo.info.title)
      Some(result(html, "text/html"))

    case List("openapi.json") =>
      val openapi = OpenAPIDocsInterpreter(OpenAPIDocsOptions.default)
        .serverEndpointsToOpenAPI[F](endpoints.filter(docInfo.filterEndpoints), docInfo.info)
      val json    = openapi.asJson.spaces2SortKeys
      Some(result(json, "application/json"))

    case _ => None
  }
}
