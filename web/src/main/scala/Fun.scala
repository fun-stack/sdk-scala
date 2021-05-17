package funstack.web

import cats.effect.IO
import mycelium.js.core.JsMessageBuilder
import mycelium.core.message._
import chameleon.{Serializer, Deserializer}
import funstack.core._

import org.scalajs.dom

object Fun {
  val auth = AppConfig.auth.map { auth =>
    new Auth[IO](
      AuthConfig(
        baseUrl = Url(s"https://${auth.domain}"),
        redirectUrl = Url(dom.window.location.origin.getOrElse(AppConfig.website.domain)),
        clientId = ClientId(auth.clientIdAuth),
        region = Region(AppConfig.region),
        identityPoolId = IdentityPoolId(auth.identityPoolId),
        cognitoEndpoint = Url(auth.cognitoEndpoint),
      ),
    )
  }

  val api = AppConfig.api.map(api => new Api(api))
}
