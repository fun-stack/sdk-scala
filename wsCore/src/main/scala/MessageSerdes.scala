package funstack.ws.core

import funstack.core.{CanSerialize, SubscriptionEvent}

import mycelium.core.message._
import chameleon.{Serializer, Deserializer}

import scala.util.Try
import scala.scalajs.js
import scala.scalajs.js.JSConverters._

object ServerMessageSerdes {
  case class UnknownMessage(msg: String) extends Exception

  object serializer {
    val pong: String = js.JSON.stringify(js.Array[js.Any](0))
    val notification: SubscriptionEvent => String = event => js.JSON.stringify(js.Array[js.Any](4, event.subscriptionKey, event.body))
  }

  def serialize[T: CanSerialize](msg: ServerMessage[T, SubscriptionEvent, T]): String = msg match {
    case Pong => serializer.pong
    case CallResponse(seqId, result) => js.JSON.stringify(js.Array[js.Any](1, seqId, CanSerialize[T].serialize(result)))
    case CallResponseFailure(seqId, failure) => js.JSON.stringify(js.Array[js.Any](2, seqId, CanSerialize[T].serialize(failure)))
    case CallResponseException(seqId) => js.JSON.stringify(js.Array[js.Any](3, seqId))
    case Notification(event) => serializer.notification(event)
  }

  def deserialize[T: CanSerialize](serialized: String): Either[Throwable, ServerMessage[T, SubscriptionEvent, T]] = Try {
    val parsed = js.JSON.parse(serialized)
    val list = parsed.asInstanceOf[js.Array[Any]].toList
    list match {
      case 0 :: Nil => Right(Pong)
      case 1 :: (seqId: Int) :: (result: String) :: Nil => CanSerialize[T].deserialize(result).map(CallResponse(seqId, _))
      case 2 :: (seqId: Int) :: (failure: String) :: Nil => CanSerialize[T].deserialize(failure).map(CallResponseFailure(seqId, _))
      case 3 :: (seqId: Int) :: Nil => Right(CallResponseException(seqId))
      case 4 :: (subscriptionKey: String) :: (body: String) :: Nil => Right(Notification(SubscriptionEvent(subscriptionKey, body)))
      case _ => Left(UnknownMessage(s"Expected server message array. Got: $serialized"))
    }
  }.toEither.flatten

  object implicits {
    implicit def serverMessageSerializer[T: CanSerialize]: Serializer[ServerMessage[T, SubscriptionEvent,  T], String] = message => serialize[T](message)
    implicit def serverMessageDeserializer[T: CanSerialize]: Deserializer[ServerMessage[T, SubscriptionEvent,  T], String] = message => deserialize[T](message)
  }
}

object ClientMessageSerdes {
  case class UnknownMessage(msg: String) extends Exception

  def serialize[T: CanSerialize](msg: ClientMessage[T]): String = msg match {
    case Ping => js.JSON.stringify(js.Array[js.Any](0))
    case CallRequest(seqId, path, payload) => js.JSON.stringify(js.Array[js.Any](1, seqId, path.mkString("/"), CanSerialize[T].serialize(payload)))
  }

  def deserialize[T: CanSerialize](serialized: String): Either[Throwable, ClientMessage[T]] = Try {
    val parsed = js.JSON.parse(serialized)
    val list = parsed.asInstanceOf[js.Array[Any]].toList
    list match {
      case 0 :: Nil => Right(Ping)
      case 1 :: (seqId: Int) :: (path: String) :: (payload: String) :: Nil => CanSerialize[T].deserialize(payload).map(CallRequest(seqId, path.split("/").toList, _))
      case _ => Left(UnknownMessage(s"Expected client message array. Got: $list"))
    }
  }.toEither.flatten

  object implicits {
    implicit def clientMessageSerializer[T: CanSerialize]: Serializer[ClientMessage[T], String] = message => serialize[T](message)
    implicit def clientMessageDeserializer[T: CanSerialize]: Deserializer[ClientMessage[T], String] = message => deserialize[T](message)
  }
}
