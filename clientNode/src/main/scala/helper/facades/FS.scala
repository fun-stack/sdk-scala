package funstack.client.node.helper.facades

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport

@js.native
@JSImport("fs", JSImport.Namespace)
object FS extends js.Object {
  def existsSync(path: String): Boolean                  = js.native
  def readFileSync(path: String): BufferToString         = js.native
  def writeFileSync(path: String, content: String): Unit = js.native
  def mkdirSync(path: String): Unit                      = js.native
  def rmSync(path: String): Unit                         = js.native
}

@js.native
trait BufferToString extends js.Object {
  def toString(encoding: String): String = js.native
}
