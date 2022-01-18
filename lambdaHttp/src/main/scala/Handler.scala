package funstack.lambda.http

import net.exoego.facade.aws_lambda._
import cats.effect.{IO, Sync, ExitCase}
import cats.implicits._
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.interceptor.RequestResult
import scala.concurrent.Future
import scala.scalajs.js
import scala.scalajs.js.JSConverters._

object Handler {
  import scala.concurrent.ExecutionContext.Implicits.global

  type FunctionType = js.Function2[APIGatewayProxyEventV2, Context, js.Promise[APIGatewayProxyStructuredResultV2]]

  def handle(
      endpoints: List[ServerEndpoint[_, IO]],
  ): FunctionType = handleF[IO](endpoints, _.unsafeToFuture())

  def handleF[F[_]: Sync](
      endpoints: List[ServerEndpoint[_, F]],
      execute: F[APIGatewayProxyStructuredResultV2] => Future[APIGatewayProxyStructuredResultV2]
  ): FunctionType = { (event, context) =>
    // println(js.JSON.stringify(event))
    // println(js.JSON.stringify(context))

    val interpreter = LambdaServerInterpreter[F](event)

    val run = interpreter(new LambdaServerRequest(event), endpoints).map {
      case RequestResult.Response(response) =>
        println(response)
        response.body.getOrElse(APIGatewayProxyStructuredResultV2(statusCode = 404))
      case RequestResult.Failure(errors) =>
        println(s"No response, errors: ${errors.mkString(", ")}")
        APIGatewayProxyStructuredResultV2(statusCode = 404)
    }

    execute(run).toJSPromise
  }

  def handleFuture(
      endpoints: List[ServerEndpoint[_, Future]],
  ): FunctionType = handleF[Future](endpoints, identity)(new Sync[Future] { //TODO: do not implement Sync for sttp here. Better to somehow copy ServerEndpoints with different result type
    def pure[A](x: A): Future[A] = Future.successful(x)
    def handleErrorWith[A](fa: Future[A])(f: Throwable => Future[A]): Future[A] = fa.recoverWith { case t => f(t) }
    def raiseError[A](e: Throwable): Future[A] = Future.failed(e)
    def bracketCase[A, B](acquire: Future[A])(use: A => Future[B])(release: (A, cats.effect.ExitCase[Throwable]) => Future[Unit]): Future[B] = acquire.flatMap { a =>
      use(a)
        .flatMap(b => release(a, ExitCase.Completed).as(b))
        .recoverWith { case t => release(a, ExitCase.error(t)).flatMap(_ => Future.failed(t)) }
    }
    def flatMap[A, B](fa: Future[A])(f: A => Future[B]): Future[B] = fa.flatMap(f)
    def tailRecM[A, B](a: A)(f: A => Future[Either[A,B]]): Future[B] = f(a).flatMap {
      case Right(value) => Future.successful(value)
      case Left(another) => tailRecM(another)(f)
    }
    def suspend[A](thunk: => Future[A]): Future[A] = thunk
  })
}
