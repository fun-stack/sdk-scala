package funstack.web

import funstack.core.{SubscriptionEvent, StringSerdes}
import colibri._

import mycelium.core.client.IncidentHandler

final class EventSubscriber(send: StringSerdes => Unit) extends IncidentHandler[SubscriptionEvent] {
  import scala.collection.mutable

  private def subscribePayload(subscriptionKey: String) =
    StringSerdes(s"""{"__action": "subscribe", "subscription_key": "${subscriptionKey}" }""")
  private def unsubscribePayload(subscriptionKey: String) =
    StringSerdes(s"""{"__action": "unsubscribe", "subscription_key": "${subscriptionKey}" }""")

  private val subscriptionByKey = mutable.HashMap[String, PublishSubject[StringSerdes]]()

  private def doSubscribe(subscriptionKey: String): Unit = {
    send(subscribePayload(subscriptionKey))
  }

  private def doUnsubscribe(subscriptionKey: String): Unit = {
    subscriptionByKey.remove(subscriptionKey)
    send(unsubscribePayload(subscriptionKey))
  }

  override def onConnect(): Unit = subscriptionByKey.keys.foreach(doSubscribe)

  override def onClose(): Unit = ()

  override def onEvent(event: SubscriptionEvent): Unit = subscriptionByKey.get(event.subscriptionKey).foreach(_.onNext(event.body))

  def subscribe(subscriptionKey: String, observer: Observer[StringSerdes]): Cancelable = {
    val subject = subscriptionByKey.getOrElseUpdate(subscriptionKey, Subject.publish)
    if (!subject.hasSubscribers) doSubscribe(subscriptionKey)

    val cancelable = subject.subscribe(observer)

    Cancelable { () =>
      cancelable.cancel()
      if (!subject.hasSubscribers) doUnsubscribe(subscriptionKey)
    }
  }
}
