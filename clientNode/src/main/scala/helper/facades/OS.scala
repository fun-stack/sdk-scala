package funstack.client.node.helper.facades

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport

@js.native
@JSImport("os", JSImport.Namespace)
object OS extends js.Object {
  def homedir(): String = js.native
}
