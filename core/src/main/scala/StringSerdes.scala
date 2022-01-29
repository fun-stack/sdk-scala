package funstack.core

import chameleon.{Serializer, Deserializer}
import java.nio.ByteBuffer
import com.github.marklister.base64.Base64._
import scala.util.Try

case class StringSerdes(value: String)
object StringSerdes {
  implicit def bytesSerializer[T: Serializer[*, ByteBuffer]]: Serializer[T, StringSerdes] =
    Serializer[T, ByteBuffer].mapSerialize { bytes =>
      val byteArray =
        if (bytes.hasArray) bytes.array
        else {
          val array = new Array[Byte](bytes.remaining)
          bytes.rewind()
          bytes.get(array)
          array
        }
      StringSerdes(byteArray.toBase64)
    }

  implicit def bytesDeserializer[T: Deserializer[*, ByteBuffer]]: Deserializer[T, StringSerdes] =
    Deserializer[T, ByteBuffer].flatmapDeserialize { base64 =>
      Try(base64.value.toByteArray).toEither.map(ByteBuffer.wrap)
    }

  implicit def stringSerializer[T: Serializer[*, String]]: Serializer[T, StringSerdes] =
    Serializer[T, String].mapSerialize(StringSerdes.apply)

  implicit def stringDeserializer[T: Deserializer[*, String]]: Deserializer[T, StringSerdes] =
    Deserializer[T, String].mapDeserialize(_.value)
}
