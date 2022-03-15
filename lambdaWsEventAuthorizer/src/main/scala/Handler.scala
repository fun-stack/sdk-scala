package funstack.lambda.ws.eventauthorizer

import funstack.core.{CanSerialize, SubscriptionEvent}
import funstack.ws.core.ServerMessageSerdes

import net.exoego.facade.aws_lambda._
import facade.amazonaws.services.sns._
import mycelium.core.message._
import sloth._

import cats.effect.IO
import cats.data.Kleisli

import scala.scalajs.js
import scala.scalajs.js.JSConverters._
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

case class AuthInfo(sub: String)
case class Message(auth: Option[AuthInfo])

object Handler {
  type FunctionType = js.Function2[SNSEvent, Context, js.Promise[Unit]]

  type FutureFunc[Out]    = (Message, Out) => Future[Boolean]
  type FutureKleisli[Out] = Kleisli[Future, (Message, Out), Boolean]
  type IOFunc[Out]        = (Message, Out) => IO[Boolean]
  type IOKleisli[Out]     = Kleisli[IO, (Message, Out), Boolean]

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
    router: Message => Router[T, IOFunc1],
  ): FunctionType = handleFCustom[T, IOFunc1](router, (f, arg, _) => f(arg).unsafeToFuture())

  def handleKleisli[T: CanSerialize](
    router: Message => Router[T, IOKleisli1],
  ): FunctionType = handleFCustom[T, IOKleisli1](router, (f, arg, _) => f(arg).unsafeToFuture())

  def handleFutureKleisli[T: CanSerialize](
    router: Message => Router[T, FutureKleisli1],
  ): FunctionType = handleFCustom[T, FutureKleisli1](router, (f, arg, _) => f(arg))

  def handleFutureFunc[T: CanSerialize](
    router: Message => Router[T, FutureFunc1],
  ): FunctionType = handleFCustom[T, FutureFunc1](router, (f, arg, _) => f(arg))

  def handleFWithContext[T: CanSerialize, F[_]](
    router: Router[T, F],
    execute: (F[T], T, Message) => Future[Boolean],
  ): FunctionType = handleFCustom[T, F](_ => router, execute)

  def handleFCustom[T: CanSerialize, F[_]](
    routerf: Message => Router[T, F],
    execute: (F[T], T, Message) => Future[Boolean],
  ): FunctionType = {
    val config = FunConfig.load()

    val sendEvent: (String, String) => Future[Unit] = (config.eventsSnsTopic, config.devEnvironment.flatMap(_.sendConnection.toOption)) match {
      case (Some(eventsSnsTopic), _) =>
        val snsClient = new SNS()

        { (connectionId, data) =>
          val params = PublishInput(
            Message = data,
            MessageAttributes = js.Dictionary("connection_id" -> MessageAttributeValue(DataType = "String", StringValue = connectionId)),
            TopicArn = eventsSnsTopic,
          )

          snsClient.publishFuture(params).map(_ => ())
        }

      case (None, Some(sendConnection)) =>
        (connectionId, data) =>
          sendConnection(connectionId, data)
          Future.successful(())

      case _ =>
        (connectionId, _) =>
          println(s"Would send event to $connectionId")
          Future.successful(())
    }

    { (event, _) =>
      // println(js.JSON.stringify(event))
      // println(js.JSON.stringify(context))

      val record = event.Records(0)

      val auth    = record.Sns.MessageAttributes.get("user_id").map(attr => AuthInfo(sub = attr.Value))
      val request = Message(auth)
      val router  = routerf(request)

      val result: Future[Boolean] = ServerMessageSerdes.deserialize(record.Sns.Message) match {
        case Right(n: Notification[SubscriptionEvent]) =>
          val (a, b, arg) = n.event.subscriptionKey.split("/") match {
            case Array(a, b)      => (a, b, "")
            case Array(a, b, arg) => (a, b, arg)
            case _                => ???
          }
          CanSerialize[T].deserialize(arg) match {
            case Right(arg)  =>
              router(Request(List(a, b), arg)) match {
                case Right(result) =>
                  CanSerialize[T].deserialize(n.event.body) match {
                    case Right(body) => execute(result, body, request)
                    case Left(error) => Future.failed(new Exception(s"Deserialization Error - ${error}"))
                  }
                case Left(error)   => Future.failed(new Exception(s"Server Failure - ${error}"))
              }
            case Left(error) => Future.failed(new Exception(s"Deserialization Error - ${error}"))
          }
        case Right(s)                                  => Future.failed(new Exception(s"Unexpected event body: $s"))
        case Left(error)                               => Future.failed(new Exception(s"Deserialization Error - ${error}"))
      }

      result.flatMap {
        case true  =>
          println("Allowed")
          sendEvent(record.Sns.MessageAttributes("connection_id").Value, record.Sns.Message)
        case false =>
          println("Rejected")
          Future.successful(())
      }.toJSPromise
    }
  }
}
