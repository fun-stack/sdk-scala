package funstack.lambda.ws.rpc.helper

import scala.scalajs.js
import net.exoego.facade.aws_lambda._

@js.native
trait APIGatewayWSRequestContext extends js.Object {
  def authorizer: js.UndefOr[js.Dictionary[String]] = js.native
  def apiId: String                                 = js.native
  def connectedAt: Double                           = js.native
  def connectionId: String                          = js.native
  def domainName: String                            = js.native
  def eventType: String                             = js.native
  def extendedRequestId: String                     = js.native
  def identity: APIGatewayEventIdentity             = js.native
  def messageDirection: String                      = js.native
  def messageId: String                             = js.native
  def stage: String                                 = js.native
  def requestId: String                             = js.native
  def requestTime: String                           = js.native
  def requestTimeEpoch: Double                      = js.native
  def routeKey: String                              = js.native
}

@js.native
trait APIGatewayWSEvent extends js.Object {
  def requestContext: APIGatewayWSRequestContext = js.native
  def body: String                               = js.native
  def isBase64Encoded: Boolean                   = js.native
}
