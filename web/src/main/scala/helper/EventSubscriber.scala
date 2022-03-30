package funstack.web.helper

import funstack.core.SubscriptionEvent
import colibri._

import mycelium.core.client.IncidentHandler

final class EventSubscriber(send: String => Unit) extends IncidentHandler[SubscriptionEvent] {
  import scala.collection.mutable

  private def subscribePayload(subscriptionKey: String)   =
    s"""{"__action": "subscribe", "subscription_key": "${subscriptionKey}" }"""
  private def unsubscribePayload(subscriptionKey: String) =
    s"""{"__action": "unsubscribe", "subscription_key": "${subscriptionKey}" }"""

  private val subscriptionByKey = mutable.HashMap[String, PublishSubject[String]]()

  private def doSubscribe(subscriptionKey: String): Unit =
    send(subscribePayload(subscriptionKey))

  private def doUnsubscribe(subscriptionKey: String): Unit = {
    subscriptionByKey.remove(subscriptionKey)
    send(unsubscribePayload(subscriptionKey))
  }

  override def onConnect(): Unit = subscriptionByKey.keys.foreach(doSubscribe)

  override def onClose(): Unit = ()

  override def onEvent(event: SubscriptionEvent): Unit = subscriptionByKey.get(event.subscriptionKey).foreach(_.unsafeOnNext(event.body))

  def subscribe(subscriptionKey: String, observer: Observer[String]): Cancelable = {
    val subject = subscriptionByKey.getOrElseUpdate(subscriptionKey, Subject.publish())
    if (!subject.hasSubscribers) doSubscribe(subscriptionKey)

    val cancelable = subject.unsafeSubscribe(observer)

    val unsubscribe = Cancelable(() => if (!subject.hasSubscribers) doUnsubscribe(subscriptionKey))

    Cancelable.composite(cancelable, unsubscribe)
  }
}
