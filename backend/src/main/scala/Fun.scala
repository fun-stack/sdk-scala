package funstack.backend

object Fun {
  def ws[Event] = Option(System.getenv("FUN_WEBSOCKET_CONNECTIONS_DYNAMODB_TABLE")).map(new Ws[Event](_))
}
