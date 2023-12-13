package funstack.core

case class HttpResponseError(msg: String, statusCode: Int) extends Exception(s"Http response error ($statusCode): ${msg}") {
  override def toString(): String = getMessage
}
