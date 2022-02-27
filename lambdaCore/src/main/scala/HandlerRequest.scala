package funstack.lambda.core

import net.exoego.facade.aws_lambda.Context

case class AuthInfo(sub: String, username: String)
case class HandlerRequest[T](event: T, context: Context, auth: Option[AuthInfo])
