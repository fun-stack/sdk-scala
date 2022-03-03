package funstack

import scala.scalajs.js

package object web {
  type AppConfig        = AppConfigTyped[js.Dictionary[String]]
  type WebsiteAppConfig = WebsiteAppConfigTyped[js.Dictionary[String]]
}
