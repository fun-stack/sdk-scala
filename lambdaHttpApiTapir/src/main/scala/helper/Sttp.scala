package funstack.lambda.http.api.tapir.helper

import cats.effect.Sync
import cats.implicits._
import sttp.tapir._
import sttp.tapir.server.interpreter._
import sttp.tapir.server.ServerEndpoint
import sttp.capabilities.Streams
import sttp.tapir.model.{ConnectionInfo, ServerRequest}
import sttp.model.{HasHeaders, Header, Method, QueryParams, Uri}
import net.exoego.facade.aws_lambda._

import java.nio.charset.Charset

import scala.scalajs.js.|
import scala.scalajs.js.JSConverters._

object UnitStreams extends Streams[Unit] {
  type BinaryStream = Unit
  type Pipe[A, B]   = Unit
}

object LambdaToResponseBody extends ToResponseBody[APIGatewayProxyStructuredResultV2, Unit] {
  val streams                                                                                                                      = UnitStreams
  def fromRawValue[R](v: R, headers: HasHeaders, format: CodecFormat, bodyType: RawBodyType[R]): APIGatewayProxyStructuredResultV2 =
    bodyType match {
      case RawBodyType.StringBody(charset) =>
        APIGatewayProxyStructuredResultV2(
          statusCode = 200,
          body = new String(v.getBytes(charset)),
          headers = headers.headers.map(header => header.name -> (header.value: Boolean | Double | String)).toMap.toJSDictionary,
        )
      case _                               => APIGatewayProxyStructuredResultV2(statusCode = 500, body = "Not implemented")
    }
  def fromStreamValue(
    v: streams.BinaryStream,
    headers: HasHeaders,
    format: CodecFormat,
    charset: Option[Charset],
  ): APIGatewayProxyStructuredResultV2 = APIGatewayProxyStructuredResultV2(statusCode = 500, body = "Not implemented")

  def fromWebSocketPipe[REQ, RESP](
    pipe: streams.Pipe[REQ, RESP],
    o: WebSocketBodyOutput[streams.Pipe[REQ, RESP], REQ, RESP, _, Unit],
  ): APIGatewayProxyStructuredResultV2 = APIGatewayProxyStructuredResultV2(statusCode = 500, body = "Not implemented")
}

class LambdaRequestBody[F[_]: Sync](event: APIGatewayProxyEventV2) extends RequestBody[F, Unit] {
  val streams                                            = UnitStreams
  def toRaw[R](bodyType: RawBodyType[R]): F[RawValue[R]] =
    bodyType match {
      case RawBodyType.StringBody(charset) => Sync[F].pure(RawValue(new String(event.body.getOrElse("").getBytes(charset))))
      case _                               => Sync[F].raiseError(new NotImplementedError)
    }
  def toStream(): streams.BinaryStream                   = ()
}

class LambdaServerRequest(event: APIGatewayProxyEventV2) extends ServerRequest {
  def protocol: String               = event.requestContext.http.protocol
  def connectionInfo: ConnectionInfo = ConnectionInfo(local = None, remote = None, secure = None) // TODO?
  def underlying: Any                = event

  def pathSegments: List[String]   = event.requestContext.http.path.split("/").toList.drop(2)
  def queryParameters: QueryParams = QueryParams.fromMap(event.queryStringParameters.fold(Map.empty[String, String])(_.toMap))

  def headers: Seq[Header] = event.headers.map { case (key, value) => Header(key, value) }.toSeq
  def method: Method       = Method(event.requestContext.http.method)
  def uri: Uri             = Uri.unsafeApply(scheme = "https", host = event.requestContext.domainName, path = event.requestContext.stage +: pathSegments)
}

object LambdaServerInterpreter {
  import sttp.client3.impl.cats.implicits._

  private implicit def catsSttpBodyListener[F[_]: Sync, B]: BodyListener[F, B] = new BodyListener[F, B] {
    def onComplete(body: B)(cb: util.Try[Unit] => F[Unit]): F[B] = Sync[F].defer(cb(util.Success(()))).as(body)
  }

  def apply[F[_]: Sync](endpoints: List[ServerEndpoint[_, F]]) = new ServerInterpreter(
    serverEndpoints = endpoints.asInstanceOf[List[ServerEndpoint[Any, F]]],
    toResponseBody = LambdaToResponseBody,
    interceptors = Nil,
    deleteFile = _ => Sync[F].unit,
  )
}
