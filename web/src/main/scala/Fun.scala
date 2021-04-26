package funstack.web

import cats.effect.{Async, IO}
import cats.MonadError
import sloth.{Client, ClientException}
import mycelium.js.core.JsMessageBuilder
import mycelium.core.message._
import chameleon.{Serializer, Deserializer}
import funstack.core._

import org.scalajs.dom

object Fun {
  val auth = new Auth[IO](
    AuthConfig(
      baseUrl = Url(s"https://${AppConfig.domainAuth}"),
      redirectUrl = Url(dom.window.location.origin.getOrElse(AppConfig.domain)),
      clientId = ClientId(AppConfig.clientIdAuth),
      region = Region(AppConfig.region),
      identityPoolId = IdentityPoolId(AppConfig.identityPoolId),
      cognitoEndpoint = Url(AppConfig.cognitoEndpoint),
    ),
  )

  object api {
    import Base64Serdes._

    val ws = new Websocket(WebsocketConfig(baseUrl = Url(s"wss://${AppConfig.domainWS}"), allowUnauthenticated = AppConfig.allowUnauthenticated))

    def wsClient[PickleType, F[_]: Async](implicit
        serializer: Serializer[ClientMessage[PickleType], String],
        deserializer: Deserializer[ServerMessage[PickleType, String, String], String],
    ) =
      Client[PickleType, F, ClientException](ws.transport[String, String, PickleType, F])
  }
}
