package funstack.lambda

import net.exoego.facade.aws_lambda._

import scala.scalajs.js
import mycelium.core.message._
import sloth._
import chameleon.{Serializer, Deserializer}

object Handler {

  def handle[T, Event, Failure, F[_]](
      routerF: Router[T, F] => Router[T, F],
      event: APIGatewayWSEvent,
      convert: F[T] => js.Promise[Either[Failure, T]],
  )(implicit
      deserializer: Deserializer[ClientMessage[T], String],
      serializer: Serializer[ServerMessage[T, Event, Failure], String],
  ) = {
    println(js.JSON.stringify(event))
    val router = routerF(Router[T, F])
    val result: js.Promise[ServerMessage[T, Event, Failure]] = Deserializer[ClientMessage[T], String].deserialize(event.body) match {
      case Left(error) => js.Promise.reject(new Exception(s"Deserializer: $error"))
      case Right(Ping) => js.Promise.resolve[Pong.type](Pong)
      case Right(CallRequest(seqNumber, path, payload)) =>
        router(Request(path, payload)).toEither match {
          case Right(result) => convert(result).`then`[ServerMessage[T, Event, Failure]](CallResponse(seqNumber, _))
          case Left(error)   => js.Promise.reject(new Exception(error.toString))
        }
    }

    result
      .`then`[String](Serializer[ServerMessage[T, Event, Failure], String].serialize)
      .`then`[APIGatewayProxyStructuredResultV2](
        payload => APIGatewayProxyStructuredResultV2(body = payload, statusCode = 200),
        (((e: Any) => APIGatewayProxyStructuredResultV2(body = e.toString, statusCode = 500)): js.Function1[
          Any,
          APIGatewayProxyStructuredResultV2,
        ]): js.UndefOr[js.Function1[Any, APIGatewayProxyStructuredResultV2]],
      )
      .`then`[APIGatewayProxyStructuredResultV2]({ (result: APIGatewayProxyStructuredResultV2) =>
        println(js.JSON.stringify(result))
        result: APIGatewayProxyStructuredResultV2
      }: js.Function1[APIGatewayProxyStructuredResultV2, APIGatewayProxyStructuredResultV2])
  }
}
