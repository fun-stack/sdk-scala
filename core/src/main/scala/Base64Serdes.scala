package funstack.core

import chameleon.{Serializer, Deserializer}
import java.nio.ByteBuffer
import com.github.marklister.base64.Base64._
import scala.util.Try

object Base64Serdes {

  implicit def serializer[T: Serializer[*, ByteBuffer]]: Serializer[T, String] =
    Serializer[T, ByteBuffer].mapSerialize { bytes =>
      val byteArray =
        if (bytes.hasArray) bytes.array
        else {
          val array = new Array[Byte](bytes.remaining)
          bytes.rewind()
          bytes.get(array)
          array
        }
      byteArray.toBase64
    }

  implicit def deserializer[T: Deserializer[*, ByteBuffer]]: Deserializer[T, String] =
    Deserializer[T, ByteBuffer].flatmapDeserialize { base64 =>
      Try(base64.toByteArray).toEither.map(ByteBuffer.wrap)
    }
}
