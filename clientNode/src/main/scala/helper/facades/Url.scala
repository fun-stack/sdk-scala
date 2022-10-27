package funstack.client.node.helper.facades

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport

@js.native
@JSImport("url", JSImport.Namespace)
object Url extends js.Object {
  def parse(url: String, parseQueryString: Boolean = js.native): ParsedUrl = js.native
}

@js.native
trait ParsedUrl extends js.Object {
  def query: js.Dictionary[String] = js.native
}

trait UrlParseOptions extends js.Object {}
