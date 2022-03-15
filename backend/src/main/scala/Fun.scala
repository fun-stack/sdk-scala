package funstack.backend

object Fun {
  val config = Config.load()

  val authOption =
    config.fun.cognitoUserPoolId
      .map(new AuthAws(_))
      .orElse(config.fun.devEnvironment.flatMap(_.getEmail.toOption).map(new AuthDev(_)))

  val wsOption =
    config.fun.eventsSnsTopic
      .map(new WsOperationsAWS(_))
      .orElse(config.fun.devEnvironment.flatMap(_.sendSubscription.toOption).map(new WsOperationsDev(_)))
      .map(new Ws(_))

  case class MissingModuleException(name: String) extends Exception(s"Missing module: $name")

  lazy val auth = authOption.getOrElse(throw MissingModuleException("auth"))
  lazy val ws   = wsOption.getOrElse(throw MissingModuleException("ws"))
}
