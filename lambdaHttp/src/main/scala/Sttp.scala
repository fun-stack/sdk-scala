package funstack.lambda.http

import sttp.monad.{MonadError => SttpMonadError}
import cats.effect.Sync
import cats.implicits._
import sttp.tapir._
import sttp.tapir.server.interpreter._
import sttp.capabilities.Streams
import sttp.tapir.model.{ServerRequest, ConnectionInfo}
import sttp.model.{QueryParams, HasHeaders, Header, Method, Uri}
import net.exoego.facade.aws_lambda._

import java.nio.charset.Charset
import java.nio.ByteBuffer
import java.io.{File, ByteArrayInputStream}

import scala.scalajs.js.|
import scala.scalajs.js.JSConverters._

//TODO
trait LambdaStreams extends Streams[LambdaStreams] {
  type BinaryStream = String
  type Pipe[A, B]   = A => B
}
object LambdaStreams extends LambdaStreams

class LambdaRequestBody[F[_]: Sync](event: APIGatewayProxyEventV2) extends RequestBody[F, LambdaStreams] {
  val streams: LambdaStreams = LambdaStreams
  def toRaw[R](bodyType: RawBodyType[R]): F[RawValue[R]] =
    bodyType match {
      case RawBodyType.StringBody(charset) => Sync[F].pure(RawValue(new String(event.body.getOrElse("").getBytes(charset))))
      case RawBodyType.ByteArrayBody       => Sync[F].pure(RawValue(event.body.getOrElse("").getBytes))
      case RawBodyType.ByteBufferBody      => Sync[F].pure(RawValue(ByteBuffer.wrap(event.body.getOrElse("").getBytes)))
      case RawBodyType.InputStreamBody     => Sync[F].pure(RawValue(new ByteArrayInputStream(event.body.getOrElse("").getBytes)))
      case RawBodyType.FileBody            => Sync[F].raiseError(new NotImplementedError) // TODO
      case RawBodyType.MultipartBody(_, _) => Sync[F].raiseError(new NotImplementedError)
    }
  def toStream(): streams.BinaryStream = event.body.getOrElse("")
}

object LambdaToResponseBody extends ToResponseBody[APIGatewayProxyStructuredResultV2, LambdaStreams] {
  val streams: LambdaStreams = LambdaStreams
  def fromRawValue[R](v: R, headers: HasHeaders, format: CodecFormat, bodyType: RawBodyType[R]): APIGatewayProxyStructuredResultV2 = {
    bodyType match {
      case RawBodyType.StringBody(charset) =>
        APIGatewayProxyStructuredResultV2(
          statusCode = 200,
          body = new String(v.getBytes(charset)),
          headers = headers.headers.map(header => header.name -> (header.value: Boolean | Double | String)).toMap.toJSDictionary,
        )
      case RawBodyType.ByteArrayBody       => APIGatewayProxyStructuredResultV2(statusCode = 500) // TODO base64
      case RawBodyType.ByteBufferBody      => APIGatewayProxyStructuredResultV2(statusCode = 500) // TODO base64
      case RawBodyType.InputStreamBody     => APIGatewayProxyStructuredResultV2(statusCode = 500) // TODO base64
      case RawBodyType.FileBody            => APIGatewayProxyStructuredResultV2(statusCode = 500) // TODO base64
      case RawBodyType.MultipartBody(_, _) => APIGatewayProxyStructuredResultV2(statusCode = 500, body = "Not implemented")
    }
  }
  def fromStreamValue(
      v: streams.BinaryStream,
      headers: HasHeaders,
      format: CodecFormat,
      charset: Option[Charset],
  ): APIGatewayProxyStructuredResultV2 = fromRawValue(v, headers, format, RawBodyType.StringBody(charset.getOrElse(Charset.defaultCharset)))
  def fromWebSocketPipe[REQ, RESP](
      pipe: streams.Pipe[REQ, RESP],
      o: WebSocketBodyOutput[streams.Pipe[REQ, RESP], REQ, RESP, _, LambdaStreams],
  ): APIGatewayProxyStructuredResultV2 = APIGatewayProxyStructuredResultV2(statusCode = 500)
}

object LambdaServerInterpreter {
  import funstack.lambda.http.implicits._

  def apply[F[_]: Sync](event: APIGatewayProxyEventV2) = new ServerInterpreter(
    requestBody = new LambdaRequestBody[F](event),
    toResponseBody = LambdaToResponseBody,
    interceptors = Nil,
    deleteFile = _ => Sync[F].unit,
  )
}

class LambdaServerRequest(event: APIGatewayProxyEventV2) extends ServerRequest {
  def protocol: String               = event.requestContext.http.protocol
  def connectionInfo: ConnectionInfo = ConnectionInfo(local = None, remote = None, secure = None) // TODO?
  def underlying: Any                = event

  def pathSegments: List[String]   = event.requestContext.http.path.split("/").toList.tail.tail
  def queryParameters: QueryParams = QueryParams.fromMap(event.queryStringParameters.fold(Map.empty[String, String])(_.toMap))

  def headers: Seq[Header] = event.headers.map { case (key, value) => Header(key, value) }.toSeq
  def method: Method       = Method(event.requestContext.http.method)
  def uri: Uri = Uri.unsafeApply(scheme = "https", host = event.requestContext.domainName, path = event.requestContext.stage +: pathSegments)
}
