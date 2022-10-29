package funstack.client.node.helper.facades

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport

@js.native
@JSImport("path", JSImport.Namespace)
object Path extends js.Object {
  def join(path: String*): String = js.native
}
