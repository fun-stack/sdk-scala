package funstack.backend

import scala.scalajs.js
import scala.scalajs.js.JSConverters._

import facade.amazonaws.services.apigatewaymanagementapi.Data

import chameleon._

sealed trait WsPickleType { def dataValue: Data }
object WsPickleType {
  import java.nio.ByteBuffer

  class IncompatibleSerializationException(msg: String) extends Exception(msg)
  object IncompatibleSerializationException {
    def expectedBytes  = new IncompatibleSerializationException("Expected string serialization, got byte array")
    def expectedString = new IncompatibleSerializationException("Expected byte array serialization, got string")
  }

  case class ByteArray(value: js.Array[Byte]) extends WsPickleType { def dataValue = value }
  case class StringValue(value: String)       extends WsPickleType { def dataValue = value }

  implicit def stringSerializer[T](implicit serializer: Serializer[T, String]): Serializer[T, WsPickleType] =
    serializer.mapSerialize(WsPickleType.StringValue(_))
  implicit def jsbyteArraySerializer[T](implicit serializer: Serializer[T, js.Array[Byte]]): Serializer[T, WsPickleType] =
    serializer.mapSerialize(WsPickleType.ByteArray(_))
  implicit def byteArraySerializer[T](implicit serializer: Serializer[T, Array[Byte]]): Serializer[T, WsPickleType] =
    serializer.mapSerialize(a => WsPickleType.ByteArray(a.toJSArray))
  implicit def byteBufferSerializer[T](implicit serializer: Serializer[T, ByteBuffer]): Serializer[T, WsPickleType] =
    serializer.mapSerialize(b => WsPickleType.ByteArray(b.array.toJSArray))

  implicit def stringDeserializer[T](implicit deserializer: Deserializer[T, String]): Deserializer[T, WsPickleType] =
    deserializer.flatmapDeserialize[WsPickleType] {
      case WsPickleType.StringValue(value) => Right(value)
      case _                               => Left(IncompatibleSerializationException.expectedString)
    }
  implicit def jsbyteArrayDeserializer[T](implicit deserializer: Deserializer[T, js.Array[Byte]]): Deserializer[T, WsPickleType] =
    deserializer.flatmapDeserialize[WsPickleType] {
      case WsPickleType.ByteArray(value) => Right(value)
      case _                             => Left(IncompatibleSerializationException.expectedBytes)
    }
  implicit def byteArrayDeserializer[T](implicit deserializer: Deserializer[T, Array[Byte]]): Deserializer[T, WsPickleType] =
    deserializer.flatmapDeserialize[WsPickleType] {
      case WsPickleType.ByteArray(value) => Right(value.toArray)
      case _                             => Left(IncompatibleSerializationException.expectedBytes)
    }
  implicit def byteBufferDeserializer[T](implicit deserializer: Deserializer[T, ByteBuffer]): Deserializer[T, WsPickleType] =
    deserializer.flatmapDeserialize[WsPickleType] {
      case WsPickleType.ByteArray(value) => Right(ByteBuffer.wrap(value.toArray))
      case _                             => Left(IncompatibleSerializationException.expectedBytes)
    }
}
