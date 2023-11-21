package funstack.backend

import cats.implicits._
import funstack.core.MissingModuleException

object Fun {
  val config = Config.load()

  val authOption =
    (config.fun.authUrl, config.fun.cognitoUserPoolId)
      .mapN(new AuthAws(_, _))
      .orElse {
        config.fun.devEnvironment.flatMap { dev =>
          (dev.authUrl.toOption, dev.getEmail.toOption)
            .mapN(new AuthDev(_, _))
        }
      }

  val wsOption =
    config.fun.eventsSnsTopic
      .map(new WsOperationsAWS(_))
      .orElse(config.fun.devEnvironment.flatMap(_.sendSubscription.toOption).map(new WsOperationsDev(_)))
      .map(new Ws(_))

  lazy val auth = authOption.getOrElse(throw MissingModuleException("auth"))
  lazy val ws   = wsOption.getOrElse(throw MissingModuleException("ws"))
}
