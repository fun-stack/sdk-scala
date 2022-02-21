package funstack.lambda.eventauthorizer

import net.exoego.facade.aws_lambda._
import facade.amazonaws.services.sns._

import funstack.core.{SubscriptionEvent, StringSerdes}
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

  case class EventAuth(sub: String)
  case class EventRequest(auth: Option[EventAuth])

  type FunctionType = js.Function2[SNSEvent, Context, js.Promise[Unit]]

  type FutureFunc[Out]    = (EventRequest, Out) => Future[Boolean]
  type FutureKleisli[Out] = Kleisli[Future, (EventRequest, Out), Boolean]
  type IOFunc[Out]        = (EventRequest, Out) => IO[Boolean]
  type IOKleisli[Out]     = Kleisli[IO, (EventRequest, Out), Boolean]

  type FutureFunc1[Out]    = Out => Future[Boolean]
  type FutureKleisli1[Out] = Kleisli[Future, Out, Boolean]
  type IOFunc1[Out]        = Out => IO[Boolean]
  type IOKleisli1[Out]     = Kleisli[IO, Out, Boolean]

  def handleFunc(
      router: Router[StringSerdes, IOFunc],
  )(implicit deserializer: Deserializer[ServerMessage[Unit, SubscriptionEvent, Unit], StringSerdes]): FunctionType = handleFWithContext[IOFunc](router, (f, arg, ctx) => f(ctx, arg).unsafeToFuture())

  def handleKleisli(
      router: Router[StringSerdes, IOKleisli],
  )(implicit deserializer: Deserializer[ServerMessage[Unit, SubscriptionEvent, Unit], StringSerdes]): FunctionType = handleFWithContext[IOKleisli](router, (f, arg, ctx) => f(ctx -> arg).unsafeToFuture())

  def handleFutureKleisli(
      router: Router[StringSerdes, FutureKleisli],
  )(implicit deserializer: Deserializer[ServerMessage[Unit, SubscriptionEvent, Unit], StringSerdes]): FunctionType = handleFWithContext[FutureKleisli](router, (f, arg, ctx) => f(ctx -> arg))

  def handleFutureFunc(
      router: Router[StringSerdes, FutureFunc],
  )(implicit deserializer: Deserializer[ServerMessage[Unit, SubscriptionEvent, Unit], StringSerdes]): FunctionType = handleFWithContext[FutureFunc](router, (f, arg, ctx) => f(ctx, arg))

  def handleFunc(
      router: EventRequest => Router[StringSerdes, IOFunc1],
      )(implicit deserializer: Deserializer[ServerMessage[Unit, SubscriptionEvent, Unit], StringSerdes]): FunctionType = handleFCustom[IOFunc1](router, (f, arg, _) => f(arg).unsafeToFuture())

  def handleKleisli(
    router: EventRequest => Router[StringSerdes, IOKleisli1],
    )(implicit deserializer: Deserializer[ServerMessage[Unit, SubscriptionEvent, Unit], StringSerdes]): FunctionType = handleFCustom[IOKleisli1](router, (f, arg, _) => f(arg).unsafeToFuture())

  def handleFutureKleisli(
    router: EventRequest => Router[StringSerdes, FutureKleisli1],
    )(implicit deserializer: Deserializer[ServerMessage[Unit, SubscriptionEvent, Unit], StringSerdes]): FunctionType = handleFCustom[FutureKleisli1](router, (f, arg, _) => f(arg))

  def handleFutureFunc(
    router: EventRequest => Router[StringSerdes, FutureFunc1],
  )(implicit deserializer: Deserializer[ServerMessage[Unit, SubscriptionEvent, Unit], StringSerdes]): FunctionType = handleFCustom[FutureFunc1](router, (f, arg, _) => f(arg))

  def handleFWithContext[F[_]](
      router: Router[StringSerdes, F],
      execute: (F[StringSerdes], StringSerdes, EventRequest) => Future[Boolean],
  )(implicit deserializer: Deserializer[ServerMessage[Unit, SubscriptionEvent, Unit], StringSerdes]): FunctionType = handleFCustom[F](_ => router, execute)

  def handleFCustom[F[_]](
      routerf: EventRequest => Router[StringSerdes, F],
      execute: (F[StringSerdes], StringSerdes, EventRequest) => Future[Boolean],
  )(implicit deserializer: Deserializer[ServerMessage[Unit, SubscriptionEvent, Unit], StringSerdes]): FunctionType = {
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

    { (event, context) =>
      // println(js.JSON.stringify(event))
      // println(js.JSON.stringify(context))

      val record = event.Records(0)

      val auth = record.Sns.MessageAttributes.get("user_id").map { attr => EventAuth(sub = attr.Value) }
      val request = EventRequest(auth)
      val router = routerf(request)

      val result: Future[Boolean] = deserializer.deserialize(StringSerdes(record.Sns.Message)) match {
        case Right(n: Notification[SubscriptionEvent]) =>
          val (a, b, arg) = n.event.subscriptionKey.split("/") match {
            case Array(a, b) => (a, b, "")
            case Array(a, b, arg) => (a, b, arg)
          }
          router(Request(List(a,b), StringSerdes(arg))) match {
            case Right(result) => execute(result, n.event.body, request)
            case Left(error)   => Future.failed(new Exception(s"Server Failure - ${error}"))
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
