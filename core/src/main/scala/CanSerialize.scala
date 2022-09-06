package funstack.core

import funstack.core.helper.Base64Codec

import java.nio.ByteBuffer
import scala.util.Try

trait CanSerialize[T] {
  def serialize(value: T): String
  def deserialize(serialized: String): Either[Throwable, T]
}
object CanSerialize   {
  @inline def apply[T](implicit can: CanSerialize[T]): CanSerialize[T] = can

  implicit object CanSerializeBytes extends CanSerialize[ByteBuffer] {
    def serialize(value: ByteBuffer)    = Base64Codec.encode(value)
    def deserialize(serialized: String) = Try(Base64Codec.decode(serialized)).toEither
  }

  implicit object CanSerializeString extends CanSerialize[String] {
    def serialize(value: String)        = value
    def deserialize(serialized: String) = Right(serialized)
  }
}
