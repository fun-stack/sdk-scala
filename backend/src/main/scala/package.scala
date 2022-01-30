package funstack

import scala.scalajs.js

package object backend {
  type Config = ConfigTyped[js.Dictionary[String]]
}
