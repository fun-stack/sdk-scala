package funstack.lambda.http.api.tapir

import funstack.lambda.http.api.tapir.helper.DocServer
import funstack.lambda.apigateway
import net.exoego.facade.aws_lambda._
import cats.data.Kleisli
import cats.effect.{unsafe, IO, Sync}
import cats.implicits._
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.serverless.aws.lambda._
import sttp.tapir.serverless.aws.lambda.js._

import scala.scalajs.js
import scala.scalajs.js.JSConverters._
import scala.concurrent.Future

object Handler {
  import apigateway.Handler._
  import apigateway.Request

  import HandlerInstances._

  import org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits.global

  def handle(
    endpoints: List[ServerEndpoint[Any, IO]],
  ): FunctionType = handleF[IO](endpoints, _.unsafeToFuture()(unsafe.IORuntime.global))

  def handleFuture(
    endpoints: List[ServerEndpoint[Any, Future]],
  ): FunctionType = handleF[Future](endpoints, identity)

  def handleF[F[_]: Sync](
    endpoints: List[ServerEndpoint[Any, F]],
    execute: F[APIGatewayProxyStructuredResultV2] => Future[APIGatewayProxyStructuredResultV2],
  ): FunctionType = handleFWithContext[F](endpoints, (f, _) => execute(f))

  def handle(
    endpoints: Request => List[ServerEndpoint[Any, IO]],
  ): FunctionType = handleFCustom[IO](endpoints, (f, _) => f.unsafeToFuture()(unsafe.IORuntime.global))

  def handleFuture(
    endpoints: Request => List[ServerEndpoint[Any, Future]],
  ): FunctionType = handleFCustom[Future](endpoints, (f, _) => f)

  def handleF[F[_]: Sync](
    endpoints: Request => List[ServerEndpoint[Any, F]],
    execute: F[APIGatewayProxyStructuredResultV2] => Future[APIGatewayProxyStructuredResultV2],
  ): FunctionType = handleFCustom[F](endpoints, (f, _) => execute(f))

  def handleFunc(
    endpoints: List[ServerEndpoint[Any, IOFunc]],
  ): FunctionType = handleFWithContext[IOFunc](endpoints, (f, ctx) => f(ctx).unsafeToFuture()(unsafe.IORuntime.global))

  def handleKleisli(
    endpoints: List[ServerEndpoint[Any, IOKleisli]],
  ): FunctionType = handleFWithContext[IOKleisli](endpoints, (f, ctx) => f(ctx).unsafeToFuture()(unsafe.IORuntime.global))

  def handleFutureKleisli(
    endpoints: List[ServerEndpoint[Any, FutureKleisli]],
  ): FunctionType = handleFWithContext[FutureKleisli](endpoints, (f, ctx) => f(ctx))

  def handleFutureFunc(
    endpoints: List[ServerEndpoint[Any, FutureFunc]],
  ): FunctionType = handleFWithContext[FutureFunc](endpoints, (f, ctx) => f(ctx))

  def handleFWithContext[F[_]: Sync](
    endpoints: List[ServerEndpoint[Any, F]],
    execute: (F[APIGatewayProxyStructuredResultV2], Request) => Future[APIGatewayProxyStructuredResultV2],
  ): FunctionType = handleFCustom[F](_ => endpoints, execute)

  def handleFCustom[F[_]: Sync](
    endpointsf: Request => List[ServerEndpoint[Any, F]],
    execute: (F[APIGatewayProxyStructuredResultV2], Request) => Future[APIGatewayProxyStructuredResultV2],
  ): FunctionType = { (eventAny, context) =>
    // println(js.JSON.stringify(event))
    // println(js.JSON.stringify(context))

    val event     = eventAny.asInstanceOf[APIGatewayProxyEventV2]
    val auth      = event.requestContext.authorizer.toOption.flatMap { auth =>
      val authDict = auth.asInstanceOf[js.Dictionary[js.Dictionary[String]]]
      for {
        claims <- authDict.get("lambda")
        sub    <- claims.get("sub")
      } yield apigateway.AuthInfo(sub = sub)
    }
    val request   = Request(event, context, auth)
    val endpoints = endpointsf(request)

    val fullPath = event.requestContext.http.path.split("/").toList.drop(2)

    DocServer.serve(fullPath, endpoints) match {
      case Some(docResult) =>
        js.Promise.resolve[APIGatewayProxyStructuredResultV2](docResult)

      case None =>
        // need to remove the "stage" from the api gateway path for tapir to
        // understand the url.
        val jsRequest = new AwsJsRequest(
          rawPath = fullPath.mkString("/", "/", ""),
          rawQueryString = event.rawQueryString,
          headers = event.headers,
          requestContext = event.requestContext.asInstanceOf[AwsJsRequestContext],
          body = event.body,
          isBase64Encoded = event.isBase64Encoded,
        )

        val config = AwsCatsEffectServerOptions.default[F].copy(encodeResponseBody = false)
        val route  = AwsCatsEffectServerInterpreter[F](config).toRoute(endpoints)

        val responseSttp: F[AwsJsResponse] = route(AwsJsRequest.toAwsRequest(jsRequest)).map(AwsJsResponse.fromAwsResponse)
        execute(responseSttp.asInstanceOf[F[APIGatewayProxyStructuredResultV2]], request).toJSPromise
    }
  }
}

private object HandlerInstances {
  import scala.concurrent.ExecutionContext
  import scala.concurrent.duration.FiniteDuration
  import cats.effect.kernel

  // TODO: this is totally unsafe, we are doing this to reuse the Sync methods
  // for this Handler for Future. This sync instance will only be used by sttp,
  // and it works with the right methods.
  implicit def FutureSync(implicit ec: ExecutionContext): Sync[Future] = new Sync[Future] {
    def pure[A](x: A): Future[A]                                                = Future.successful(x)
    def handleErrorWith[A](fa: Future[A])(f: Throwable => Future[A]): Future[A] = fa.recoverWith { case t => f(t) }
    def raiseError[A](e: Throwable): Future[A]                                  = Future.failed(e)
    def flatMap[A, B](fa: Future[A])(f: A => Future[B]): Future[B]              = fa.flatMap(f)
    def tailRecM[A, B](a: A)(f: A => Future[Either[A, B]]): Future[B]           = f(a).flatMap {
      case Right(b) => Future.successful(b)
      case Left(a)  => tailRecM(a)(f)
    }
    def monotonic: Future[FiniteDuration]                                       = Sync[IO].monotonic.unsafeToFuture()(unsafe.IORuntime.global)
    def realTime: Future[FiniteDuration]                                        = Sync[IO].realTime.unsafeToFuture()(unsafe.IORuntime.global)
    def canceled: Future[Unit]                                                  = Sync[IO].canceled.unsafeToFuture()(unsafe.IORuntime.global)
    def forceR[A, B](fa: Future[A])(fb: Future[B]): Future[B]                   =
      Sync[IO].forceR(IO.fromFuture(IO(fa)))(IO.fromFuture(IO(fb))).unsafeToFuture()(unsafe.IORuntime.global)
    def onCancel[A](fa: Future[A], fin: Future[Unit]): Future[A]                =
      Sync[IO].onCancel(IO.fromFuture(IO(fa)), IO.fromFuture(IO(fin))).unsafeToFuture()(unsafe.IORuntime.global)
    def rootCancelScope: kernel.CancelScope                                     = Sync[IO].rootCancelScope
    def suspend[A](hint: kernel.Sync.Type)(thunk: => A): Future[A]              = Future(thunk)
    def uncancelable[A](body: kernel.Poll[Future] => (Future[A])): Future[A]    = Sync[IO]
      .uncancelable(poll =>
        IO.fromFuture(
          IO(
            body(
              new kernel.Poll[Future] {
                def apply[B](stfa: Future[B]): Future[B] = poll(IO.fromFuture(IO(stfa))).unsafeToFuture()(unsafe.IORuntime.global)
              },
            ),
          ),
        ),
      )
      .unsafeToFuture()(unsafe.IORuntime.global)

  }

  implicit def FuncSync[In, F[_]](implicit sync: Sync[Kleisli[F, In, *]]): Sync[Lambda[Out => In => F[Out]]] = new Sync[Lambda[Out => In => F[Out]]] {
    def pure[A](x: A): In => F[A]                                                                   = sync.pure(x).run
    def handleErrorWith[A](fa: In => F[A])(f: Throwable => (In => F[A])): In => F[A]                = sync.handleErrorWith(Kleisli(fa))(t => Kleisli(f(t))).run
    def raiseError[A](e: Throwable): In => F[A]                                                     = sync.raiseError(e).run
    def flatMap[A, B](fa: In => F[A])(f: A => (In => F[B])): In => F[B]                             = sync.flatMap(Kleisli(fa))(a => Kleisli(f(a))).run
    def tailRecM[A, B](a: A)(f: A => (In => F[Either[A, B]])): In => F[B]                           = sync.tailRecM(a)(a => Kleisli(f(a))).run
    def monotonic: In => F[FiniteDuration]                                                          = sync.monotonic.run
    def realTime: In => F[FiniteDuration]                                                           = sync.realTime.run
    def canceled: In => F[Unit]                                                                     = sync.canceled.run
    def forceR[A, B](fa: In => F[A])(fb: In => F[B]): In => F[B]                                    = sync.forceR(Kleisli(fa))(Kleisli(fb)).run
    def onCancel[A](fa: In => F[A], fin: In => F[Unit]): In => F[A]                                 = sync.onCancel(Kleisli(fa), Kleisli(fin)).run
    def rootCancelScope: kernel.CancelScope                                                         = sync.rootCancelScope
    def suspend[A](hint: kernel.Sync.Type)(thunk: => A): In => F[A]                                 = sync.suspend(hint)(thunk).run
    def uncancelable[A](body: kernel.Poll[Lambda[Out => In => F[Out]]] => (In => F[A])): In => F[A] = sync
      .uncancelable(poll =>
        Kleisli(
          body(
            new kernel.Poll[Lambda[Out => In => F[Out]]] {
              def apply[B](stfa: In => F[B]): In => F[B] = poll(Kleisli(stfa)).run
            },
          ),
        ),
      )
      .run
  }
}
