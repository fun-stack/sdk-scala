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

object implicits {
  implicit def catsSttpBodyListener[F[_]: Sync, B]: BodyListener[F, B] = new BodyListener[F, B] {
    def onComplete(body: B)(cb: util.Try[Unit] => F[Unit]): F[B] = Sync[F].defer(cb(util.Success(()))).as(body)
  }

  implicit def catsSttpMonadError[F[_]: Sync]: SttpMonadError[F] = new SttpMonadError[F] {
    def unit[T](t: T): F[T]                                                        = Sync[F].pure(t)
    def map[T, T2](fa: F[T])(f: T => T2): F[T2]                                    = fa.map(f)
    def flatMap[T, T2](fa: F[T])(f: T => F[T2]): F[T2]                             = fa.flatMap(f)
    def error[T](t: Throwable): F[T]                                               = Sync[F].raiseError(t)
    def handleWrappedError[T](rt: F[T])(h: PartialFunction[Throwable, F[T]]): F[T] = Sync[F].handleErrorWith(rt)(h)
    def ensure[T](f: F[T], e: => F[Unit]): F[T]                                    = Sync[F].guarantee(f)(e)
  }
}
