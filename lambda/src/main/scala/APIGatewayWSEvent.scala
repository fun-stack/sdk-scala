package funstack.lambda

import scala.scalajs.js
import net.exoego.facade.aws_lambda._

@js.native
trait APIGatewayWSRequestContext extends js.Object {
  def authorizer: APIGatewayAuthorizerBase = js.native
  def apiId: String                        = js.native
  def connectedAt: Double                  = js.native
  def connectionId: String                 = js.native
  def domainName: String                   = js.native
  def eventType: String                    = js.native
  def extendedRequestId: String            = js.native
  def identity: APIGatewayEventIdentity    = js.native
  def messageDirection: String             = js.native
  def messageId: String                    = js.native
  def stage: String                        = js.native
  def requestId: String                    = js.native
  def requestTime: String                  = js.native
  def requestTimeEpoch: Double             = js.native
  def routeKey: String                     = js.native
}
object APIGatewayWSRequestContext {
  implicit class Ops(val self: APIGatewayWSRequestContext) extends AnyVal {
    def authorizerWithUser: Option[APIGatewayAuthorizer] =
      if (self.authorizer.sub.isDefined) Some(self.authorizer.asInstanceOf[APIGatewayAuthorizer]) else None
  }
}

@js.native
trait APIGatewayAuthorizerBase extends js.Object {
  def principalId: String     = js.native
  def sub: js.UndefOr[String] = js.native
}

@js.native
trait APIGatewayAuthorizer extends js.Object {
  def principalId: String = js.native
  def sub: String         = js.native
  def iss: String         = js.native
  def version: String     = js.native
  def client_id: String   = js.native
  def event_id: String    = js.native
  def token_use: String   = js.native
  def scope: String       = js.native
  def auth_time: String   = js.native
  def exp: String         = js.native
  def iat: String         = js.native
  def jti: String         = js.native
  def username: String    = js.native
}

@js.native
trait APIGatewayWSEvent extends js.Object {
  def requestContext: APIGatewayWSRequestContext = js.native
  def body: String                               = js.native
  def isBase64Encoded: Boolean                   = js.native
}
