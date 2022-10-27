package funstack.client.node

import scala.annotation.nowarn
import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport

object NodePolyfills {
  // set up fetch backend to run in nodejs
  // https://sttp.softwaremill.com/en/latest/backends/javascript/fetch.html?highlight=fetch#node-js
  @js.native
  @JSImport("node-fetch", JSImport.Default)
  @nowarn
  val fetch: js.Dynamic = js.native

  @js.native
  @JSImport("node-fetch", "Headers")
  @nowarn
  val Headers: js.Dynamic = js.native

  @js.native
  @JSImport("node-fetch", "Request")
  @nowarn
  val Request: js.Dynamic = js.native

  @js.native
  @JSImport("node-fetch", "Response")
  @nowarn
  val Response: js.Dynamic = js.native

  @js.native
  @JSImport("ws", JSImport.Default)
  @nowarn
  val ws: js.Dynamic = js.native

  private var isInitialized = false

  def init(): Unit = if (!isInitialized) {
    val g = scalajs.js.Dynamic.global.globalThis

    // We are overwriting existing fetch implementations, because node 18 comes
    // with node-fetch shipped as an experimental feature. Using it, will emit
    // a warning, that we do not want. Therefore, we always use our polyfill.
    // if (!g.asInstanceOf[js.Object].hasOwnProperty("fetch")) {
    g.fetch = fetch
    g.Headers = Headers
    g.Request = Request
    g.Response = Response
    // }

    // if (!g.asInstanceOf[js.Object].hasOwnProperty("WebSocket")) {
    g.WebSocket = ws
    // }

    isInitialized = true
  }
}
