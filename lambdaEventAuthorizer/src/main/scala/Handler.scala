package funstack.lambda.ws.eventauthorizer

import net.exoego.facade.aws_lambda._
import facade.amazonaws.services.sns._

import funstack.core.{SubscriptionEvent, CanSerialize}
import funstack.lambda.core.{HandlerType, RequestOf, AuthInfo}
import funstack.ws.core.ServerMessageSerdes
import mycelium.core.message._
import scala.scalajs.js
import sloth._
import chameleon.Deserializer
import scala.scalajs.js.JSConverters._
import cats.effect.IO
import cats.data.Kleisli
import scala.concurrent.Future

import scala.concurrent.ExecutionContext.Implicits.global

object Handler {
  type FunctionType = js.Function2[SNSEvent, Context, js.Promise[Unit]]

  type EventRequest = RequestOf[SNSEvent]

  type FutureFunc[Out]    = (EventRequest, Out) => Future[Boolean]
  type FutureKleisli[Out] = Kleisli[Future, (EventRequest, Out), Boolean]
  type IOFunc[Out]        = (EventRequest, Out) => IO[Boolean]
  type IOKleisli[Out]     = Kleisli[IO, (EventRequest, Out), Boolean]

  type FutureFunc1[Out]    = Out => Future[Boolean]
  type FutureKleisli1[Out] = Kleisli[Future, Out, Boolean]
  type IOFunc1[Out]        = Out => IO[Boolean]
  type IOKleisli1[Out]     = Kleisli[IO, Out, Boolean]

  def handleFunc[T: CanSerialize](
      router: Router[T, IOFunc],
  ): FunctionType = handleFWithContext[T, IOFunc](router, (f, arg, ctx) => f(ctx, arg).unsafeToFuture())

  def handleKleisli[T: CanSerialize](
      router: Router[T, IOKleisli],
  ): FunctionType = handleFWithContext[T, IOKleisli](router, (f, arg, ctx) => f(ctx -> arg).unsafeToFuture())

  def handleFutureKleisli[T: CanSerialize](
      router: Router[T, FutureKleisli],
  ): FunctionType = handleFWithContext[T, FutureKleisli](router, (f, arg, ctx) => f(ctx -> arg))

  def handleFutureFunc[T: CanSerialize](
      router: Router[T, FutureFunc],
  ): FunctionType = handleFWithContext[T, FutureFunc](router, (f, arg, ctx) => f(ctx, arg))

  def handleFunc[T: CanSerialize](
      router: EventRequest => Router[T, IOFunc1],
  ): FunctionType = handleFCustom[T, IOFunc1](router, (f, arg, _) => f(arg).unsafeToFuture())

  def handleKleisli[T: CanSerialize](
    router: EventRequest => Router[T, IOKleisli1],
  ): FunctionType = handleFCustom[T, IOKleisli1](router, (f, arg, _) => f(arg).unsafeToFuture())

  def handleFutureKleisli[T: CanSerialize](
    router: EventRequest => Router[T, FutureKleisli1],
  ): FunctionType = handleFCustom[T, FutureKleisli1](router, (f, arg, _) => f(arg))

  def handleFutureFunc[T: CanSerialize](
    router: EventRequest => Router[T, FutureFunc1],
  ): FunctionType = handleFCustom[T, FutureFunc1](router, (f, arg, _) => f(arg))

  def handleFWithContext[T: CanSerialize, F[_]](
      router: Router[T, F],
      execute: (F[T], T, EventRequest) => Future[Boolean],
  ): FunctionType = handleFCustom[T, F](_ => router, execute)

  def handleFCustom[T: CanSerialize, F[_]](
      routerf: EventRequest => Router[T, F],
      execute: (F[T], T, EventRequest) => Future[Boolean],
  ): FunctionType = {
    val config = Config.load()

    val sendEvent: (String, String) => Future[Unit] = (config.eventsSnsTopic, config.devEnvironment) match {
      case (Some(eventsSnsTopic), _) =>
        val snsClient = new SNS()

        { (connectionId, data) =>
          val params = PublishInput(
            Message = data,
            MessageAttributes = js.Dictionary("connection_id" -> MessageAttributeValue(DataType = "String", StringValue = connectionId)),
            TopicArn = eventsSnsTopic
          )

          snsClient.publishFuture(params).map(_ => ())
        }

      case (None, Some(dev)) =>
        { (connectionId, data) =>
          dev.send_connection(connectionId, data)
          Future.successful(())
        }

      case _ =>
        { (connectionId, _) =>
          println(s"Would send event to $connectionId")
          Future.successful(())
        }
    }

    { (event, _) =>
      // println(js.JSON.stringify(event))
      // println(js.JSON.stringify(context))

      val record = event.Records(0)

      val auth = record.Sns.MessageAttributes.get("user_id").map { attr => EventAuth(sub = attr.Value) }
      val request = RequestOf(auth)
      val router = routerf(request)

      val result: Future[Boolean] = ServerMessageSerdes.deserialize(record.Sns.Message) match {
        case Right(n: Notification[SubscriptionEvent]) =>
          val (a, b, arg) = n.event.subscriptionKey.split("/") match {
            case Array(a, b) => (a, b, "")
            case Array(a, b, arg) => (a, b, arg)
            case _ => ???
          }
          CanSerialize[T].deserialize(arg) match {
            case Right(arg) => router(Request(List(a,b), arg)) match {
              case Right(result) => CanSerialize[T].deserialize(n.event.body) match {
                case Right(body) => execute(result, body, request)
                case Left(error) => Future.failed(new Exception(s"Deserialization Error - ${error}"))
              }
              case Left(error)   => Future.failed(new Exception(s"Server Failure - ${error}"))
            }
            case Left(error) => Future.failed(new Exception(s"Deserialization Error - ${error}"))
          }
        case Right(s) => Future.failed(new Exception(s"Unexpected event body: $s"))
        case Left(error) => Future.failed(new Exception(s"Deserialization Error - ${error}"))
      }

      result
        .flatMap {
          case true =>
            println("Allowed")
            sendEvent(record.Sns.MessageAttributes("connection_id").Value, record.Sns.Message)
          case false =>
            println("Rejected")
            Future.successful(())
        }
        .toJSPromise
    }
  }
}
