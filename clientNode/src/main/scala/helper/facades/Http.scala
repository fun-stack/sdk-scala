package funstack.client.node.helper.facades

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport

@js.native
@JSImport("http", JSImport.Namespace)
object Http extends js.Object {
  def createServer(): HttpServer = js.native
}

@js.native
trait HttpServer extends js.Object {
  def listen(port: Int): Unit                                                         = js.native
  def close(): Unit                                                                   = js.native
  def on(tpe: String, requestListener: js.Function2[HttpRequest, HttpResponse, Unit]) = js.native
}

@js.native
trait HttpRequest extends js.Object {
  def url: String = js.native
}

@js.native
trait HttpResponse extends js.Object {
  def writeHead(status: Int): Unit = js.native
  def end(message: String): Unit   = js.native
}
