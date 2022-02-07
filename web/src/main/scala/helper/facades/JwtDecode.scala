package funstack.web.helper.facades

import scala.scalajs.js
import scala.scalajs.js.annotation.{JSImport, JSName}

@js.native
@JSImport("jwt-decode", JSImport.Namespace)
object JwtDecode extends js.Object {
  @JSName("default")
  def apply(token: String): js.Object = js.native
}
