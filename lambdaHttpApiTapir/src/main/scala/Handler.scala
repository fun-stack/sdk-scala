package funstack.lambda.http.api.tapir

import funstack.lambda.http.api.tapir.helper._
import funstack.lambda.apigateway

import net.exoego.facade.aws_lambda._
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.interceptor.RequestResult

import cats.effect.{IO, Sync, ExitCase}
import cats.implicits._

import scala.concurrent.Future
import scala.scalajs.js
import scala.scalajs.js.JSConverters._
import scala.concurrent.ExecutionContext.Implicits.global

object Handler extends apigateway.Handler[APIGatewayProxyEventV2] {
  import SyncInstances._

  def handle(
      endpoints: List[ServerEndpoint[_, IO]],
  ): FunctionType = handleF[IO](endpoints, _.unsafeToFuture())

  def handleFuture(
      endpoints: List[ServerEndpoint[_, Future]],
  ): FunctionType = handleF[Future](endpoints, identity)

  def handleF[F[_]: Sync](
      endpoints: List[ServerEndpoint[_, F]],
      execute: F[APIGatewayProxyStructuredResultV2] => Future[APIGatewayProxyStructuredResultV2],
  ): FunctionType = handleFWithContext[F](endpoints, (f, _) => execute(f))

  def handle(
      endpoints: Request => List[ServerEndpoint[_, IO]],
  ): FunctionType = handleFCustom[IO](endpoints, (f, _) => f.unsafeToFuture())

  def handleFuture(
      endpoints: Request => List[ServerEndpoint[_, Future]],
  ): FunctionType = handleFCustom[Future](endpoints, (f, _) => f)

  def handleF[F[_]: Sync](
      endpoints: Request => List[ServerEndpoint[_, F]],
      execute: F[APIGatewayProxyStructuredResultV2] => Future[APIGatewayProxyStructuredResultV2],
  ): FunctionType = handleFCustom[F](endpoints, (f, _) => execute(f))

  def handleFunc(
      endpoints: List[ServerEndpoint[_, IOFunc]],
  ): FunctionType = handleFWithContext[IOFunc](endpoints, (f, ctx) => f(ctx).unsafeToFuture())

  def handleKleisli(
      endpoints: List[ServerEndpoint[_, IOKleisli]],
  ): FunctionType = handleFWithContext[IOKleisli](endpoints, (f, ctx) => f(ctx).unsafeToFuture())

  def handleFutureKleisli(
      endpoints: List[ServerEndpoint[_, FutureKleisli]],
  ): FunctionType = handleFWithContext[FutureKleisli](endpoints, (f, ctx) => f(ctx))

  def handleFutureFunc(
      endpoints: List[ServerEndpoint[_, FutureFunc]],
  ): FunctionType = handleFWithContext[FutureFunc](endpoints, (f, ctx) => f(ctx))

  def handleFWithContext[F[_]: Sync](
      endpoints: List[ServerEndpoint[_, F]],
      execute: (F[APIGatewayProxyStructuredResultV2], Request) => Future[APIGatewayProxyStructuredResultV2],
  ): FunctionType = handleFCustom[F](_ => endpoints, execute)

  def handleFCustom[F[_]: Sync](
      endpointsf: Request => List[ServerEndpoint[_, F]],
      execute: (F[APIGatewayProxyStructuredResultV2], Request) => Future[APIGatewayProxyStructuredResultV2],
  ): FunctionType = { (event, context) =>
    // println(js.JSON.stringify(event))
    // println(js.JSON.stringify(context))

    val auth = event.requestContext.authorizer.toOption.flatMap { auth =>
      val authDict = auth.asInstanceOf[js.Dictionary[js.Dictionary[String]]]
      for {
        claims <- authDict.get("lambda")
        sub <- claims.get("sub")
      } yield apigateway.AuthInfo(sub = sub)
    }
    val request = apigateway.RequestOf(event, context, auth)
    val endpoints   = endpointsf(request)

    val fullPath = event.requestContext.http.path.split("/").toList.drop(2)

    DocServer.serve(fullPath, endpoints) match {
      case Some(docResult) => js.Promise.resolve[APIGatewayProxyStructuredResultV2](docResult)
      case None =>
        val interpreter = LambdaServerInterpreter[F](endpoints)

        val run = interpreter(new LambdaServerRequest(event), new LambdaRequestBody[F](event)).map {
          case RequestResult.Response(response) =>
            println(response)
            response.body.getOrElse(APIGatewayProxyStructuredResultV2(statusCode = 404))
          case RequestResult.Failure(errors) =>
            println(s"No response, errors: ${errors.mkString(", ")}")
            APIGatewayProxyStructuredResultV2(statusCode = 404)
        }

        execute(run, request).toJSPromise
    }
  }
}

private object SyncInstances {

  // TODO: do not implement Sync for sttp here. This is not safe. Better to somehow copy ServerEndpoints with different result type
  implicit val UnsafeFutureSync: Sync[Future] = new Sync[Future] {
    def pure[A](x: A): Future[A]                                                = Future.successful(x)
    def handleErrorWith[A](fa: Future[A])(f: Throwable => Future[A]): Future[A] = fa.recoverWith { case t => f(t) }
    def raiseError[A](e: Throwable): Future[A]                                  = Future.failed(e)
    def bracketCase[A, B](acquire: Future[A])(use: A => Future[B])(release: (A, cats.effect.ExitCase[Throwable]) => Future[Unit]): Future[B] =
      acquire.flatMap { a =>
        use(a)
          .flatMap(b => release(a, ExitCase.Completed).map(_ => b))
          .recoverWith { case t => release(a, ExitCase.error(t)).flatMap(_ => Future.failed(t)) }
      }
    def flatMap[A, B](fa: Future[A])(f: A => Future[B]): Future[B] = fa.flatMap(f)
    def tailRecM[A, B](a: A)(f: A => Future[Either[A, B]]): Future[B] = f(a).flatMap {
      case Right(value)  => Future.successful(value)
      case Left(another) => tailRecM(another)(f)
    }
    def suspend[A](thunk: => Future[A]): Future[A] = thunk
  }

  // TODO: reuse implementation from KleisliIO?
  implicit def FuncSync[In, F[_]: Sync]: Sync[Lambda[Out => In => F[Out]]] = new Sync[Lambda[Out => In => F[Out]]] {
    def pure[A](x: A): In => F[A]                                                    = _ => Sync[F].pure(x)
    def handleErrorWith[A](fa: In => F[A])(f: Throwable => (In => F[A])): In => F[A] = in => fa(in).handleErrorWith(t => f(t)(in))
    def raiseError[A](e: Throwable): In => F[A]                                      = _ => Sync[F].raiseError(e)
    def bracketCase[A, B](acquire: In => F[A])(use: A => (In => F[B]))(release: (A, cats.effect.ExitCase[Throwable]) => (In => F[Unit])): In => F[B] =
      in => Sync[F].bracketCase(acquire(in))(a => use(a)(in))((a, e) => release(a, e)(in))
    def flatMap[A, B](fa: In => F[A])(f: A => (In => F[B])): In => F[B]   = in => fa(in).flatMap(a => f(a)(in))
    def tailRecM[A, B](a: A)(f: A => (In => F[Either[A, B]])): In => F[B] = in => Sync[F].tailRecM(a)(a => f(a)(in))
    def suspend[A](thunk: => (In => F[A])): In => F[A]                    = in => thunk(in)
  }
}
