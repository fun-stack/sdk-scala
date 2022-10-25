package funstack.core

case class MissingModuleException(name: String) extends Exception(s"Missing module: $name")
