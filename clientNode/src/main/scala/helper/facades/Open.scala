package funstack.client.node.helper.facades

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport

@js.native
@JSImport("open", JSImport.Namespace)
object Open extends js.Object {
  def apply(url: String): Unit = js.native
}
