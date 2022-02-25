package funstack.core

import chameleon.{Serializer, Deserializer}
import java.nio.ByteBuffer
import scala.util.Try

case class StringSerdes(value: String)
object StringSerdes {
  implicit def bytesSerializer[T: Serializer[*, ByteBuffer]]: Serializer[T, StringSerdes] =
    Serializer[T, ByteBuffer].mapSerialize { bytes =>
      StringSerdes(Base64Codec.encode(bytes))
    }

  implicit def bytesDeserializer[T: Deserializer[*, ByteBuffer]]: Deserializer[T, StringSerdes] =
    Deserializer[T, ByteBuffer].flatmapDeserialize { base64 =>
      Try(Base64Codec.decode(base64.value)).toEither
    }

  implicit def stringSerializer[T: Serializer[*, String]]: Serializer[T, StringSerdes] =
    Serializer[T, String].mapSerialize(StringSerdes.apply)

  implicit def stringDeserializer[T: Deserializer[*, String]]: Deserializer[T, StringSerdes] =
    Deserializer[T, String].mapDeserialize(_.value)
}
