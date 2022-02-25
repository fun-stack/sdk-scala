package funstack.core

import java.nio.ByteBuffer

import scala.scalajs.js

object Base64Codec {
  import NativeTypes._

  def encode(buffer: ByteBuffer): String = {
    val n = buffer.limit()
    val s = new StringBuilder(n)
    var i = 0
    while (i < n) {
      val c = buffer.get
      s ++= string.fromCharCode(c & 0xFF).asInstanceOf[String]
      i += 1
    }

    btoa(s.result())
  }

  def decode(base64Data: String): ByteBuffer = {
    val byteString = atob(base64Data)
    val buffer = ByteBuffer.allocateDirect(byteString.size)
    byteString.foreach(c => buffer.put(c.toByte))
    buffer.flip()
    buffer
  }
}

private object NativeTypes {
  import js.Dynamic.{global => g}

  @js.native
  trait BufferFactory extends js.Any {
    def from(@annotation.nowarn s: String, @annotation.nowarn tpe: String): Buffer = js.native
  }

  @js.native
  trait Buffer extends js.Any {
    def toString(@annotation.nowarn s: String): String = js.native
  }

  @js.native
  trait StringFactory extends js.Any {
    def fromCharCode(@annotation.nowarn code: Int): String = js.native
  }

  def buffer = g.Buffer.asInstanceOf[BufferFactory]
  def string = g.String.asInstanceOf[StringFactory]

  val atob: js.Function1[String, String] =
    if (js.isUndefined(g.atob)) (base64) => buffer.from(base64, "base64").toString("binary")
    else g.atob.asInstanceOf[js.Function1[String, String]]

  val btoa: js.Function1[String, String] =
    if (js.isUndefined(g.atob)) text => buffer.from(text, "binary").toString("base64")
    else g.btoa.asInstanceOf[js.Function1[String, String]]
}
