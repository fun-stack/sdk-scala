package funstack.web

import cats.effect.IO
import funstack.web.stripe._
import scala.scalajs.js
import scala.scalajs.js.JSConverters._
import scala.concurrent.ExecutionContext
import org.scalajs.dom

class Payment(payment: PaymentAppConfig) {
  private implicit val cs = IO.contextShift(ExecutionContext.global)

  private val loadedStripe = {
    // load stripe as side effect
    val stripe = Stripe.loadStripe(payment.publishableKey).toFuture
    IO.fromFuture(IO(stripe))
  }

  private def getSessionId(priceId: String) = IO.pure("WTF")

  private def redirectToCheckout(stripe: Stripe, stripeSessionId: String) =
    IO.fromFuture(IO(stripe.redirectToCheckout(new StripeRedirectToCheckoutOptions { sessionId = stripeSessionId }).toFuture))

  def redirectToCheckout(priceId: String): IO[StripeError] = for {
    stripe          <- loadedStripe
    stripeSessionId <- getSessionId(priceId)
    response        <- redirectToCheckout(stripe, stripeSessionId)
  } yield response.error

  val priceIdsByName: Map[String, String] = payment.priceIds.toMap
}
