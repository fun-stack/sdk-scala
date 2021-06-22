package funstack.web.stripe

import scala.scalajs.js
import scala.scalajs.js.annotation._

object Stripe {
  @js.native
  @JSImport("@stripe/stripe-js", "loadStripe")
  def loadStripe(pk: String, options: StripeConstructorOptions): js.Promise[Stripe] = js.native
}

trait StripeConstructorOptions extends js.Object {
  var stripeAccount: js.UndefOr[String]   = js.undefined
  var apiVersion: js.UndefOr[String]      = js.undefined
  var betas: js.UndefOr[js.Array[String]] = js.undefined
  var locale: js.UndefOr[String]          = js.undefined
}

trait StripeRedirectToCheckoutOptions extends js.Object {
  var sessionId: js.UndefOr[String] = js.undefined
}

@js.native
trait Stripe extends js.Object {
  def redirectToCheckout(options: StripeRedirectToCheckoutOptions): js.Promise[StripeRedirectToCheckoutError] = js.native
}

@js.native
trait StripeRedirectToCheckoutError extends js.Object {
  def error: StripeError
}

@js.native
trait StripeError extends js.Object {
  def `type`: String
  def charge: js.UndefOr[String]
  def code: js.UndefOr[String]
  def decline_code: js.UndefOr[String]
  def doc_url: js.UndefOr[String]
  def message: js.UndefOr[String]
  def param: js.UndefOr[String]
  def payment_intent: js.UndefOr[js.Any] // TODO: type
  def payment_method: js.UndefOr[js.Any] // TODO: type
  def setup_intent: js.UndefOr[js.Any]   // TODO: type
  def source: js.UndefOr[js.Any]         // TODO: type
}
