package funstack.backend

object Fun {
  val config = Config.load()

  val authOption = config.fun.cognitoUserPoolId.map(new Auth(_))

  val wsOption =
    config.fun.eventsSnsTopic
      .map(new WsOperationsAWS(_))
      .orElse(config.fun.devEnvironment.flatMap { env =>
        env.sendSubscription.map(new WsOperationsDev(_)).toOption
      })
      .map(new Ws(_))

  case class MissingModuleException(name: String) extends Exception(s"Missing module: $name")

  lazy val auth = authOption.getOrElse(throw MissingModuleException("auth"))
  lazy val ws   = wsOption.getOrElse(throw MissingModuleException("ws"))
}
