package funstack.client.core.auth

import colibri.Observable

trait Auth {
  def currentUser: Observable[Option[User]]
}
