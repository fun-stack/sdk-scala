package funstack.lambda.apigateway.helper.facades

import net.exoego.facade.aws_lambda._

import scala.scalajs.js

@js.native
trait APIGatewayWsRequestContext extends js.Object {
  def authorizer: js.UndefOr[APIGatewayAuthorizer] = js.native
  def apiId: String                                = js.native
  def connectedAt: Double                          = js.native
  def connectionId: String                         = js.native
  def domainName: String                           = js.native
  def eventType: String                            = js.native
  def extendedRequestId: String                    = js.native
  def identity: APIGatewayEventIdentity            = js.native
  def messageDirection: String                     = js.native
  def messageId: String                            = js.native
  def stage: String                                = js.native
  def requestId: String                            = js.native
  def requestTime: String                          = js.native
  def requestTimeEpoch: Double                     = js.native
  def routeKey: String                             = js.native
}

@js.native
trait APIGatewayWsEvent extends js.Object {
  def requestContext: APIGatewayWsRequestContext = js.native
  def body: String                               = js.native
  def isBase64Encoded: Boolean                   = js.native
}
