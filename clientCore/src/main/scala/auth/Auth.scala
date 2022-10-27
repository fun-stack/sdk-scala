package funstack.client.core.auth

import colibri.Observable

trait Auth {
  def loginUrl: String
  def signupUrl: String
  def logoutUrl: String

  def currentUser: Observable[Option[User]]
}
