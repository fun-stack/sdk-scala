import org.portablescala.sbtplatformdeps.PlatformDepsPlugin.autoImport._
import sbt._

object Deps {
  import Def.{setting => dep}

  // testing
  val scalatest = dep("org.scalatest" %%% "scalatest" % "3.2.0")

  // core libraries
  val cats = new {
    val core   = dep("org.typelevel" %%% "cats-core" % "2.1.1")
    val effect = dep("org.typelevel" %%% "cats-effect" % "2.3.0")
  }

  // frp
  val colibri = dep("com.github.cornerman.colibri" %%% "colibri" % "6000107")

  // rpc
  val sloth = dep("com.github.cornerman.sloth" %%% "sloth" % "c0c6ef0")

  // websocket connecitivity
  val mycelium = new {
    val version  = "2a7a14c"
    val core     = dep("com.github.cornerman.mycelium" %%% "mycelium-core" % version)
    val clientJs = dep("com.github.cornerman.mycelium" %%% "mycelium-client-js" % version)
  }

  // utils
  val base64 = dep("com.github.marklister" %%% "base64" % "0.3.0")

  // aws-sdk-js
  val awsSdkJS = new {
    val version         = s"0.32.0-v${NpmDeps.awsSdkVersion}"
    val lambda          = dep("net.exoego" %%% "aws-sdk-scalajs-facade-lambda" % version)
    val sts             = dep("net.exoego" %%% "aws-sdk-scalajs-facade-sts" % version)
    val cognitoidentity = dep("net.exoego" %%% "aws-sdk-scalajs-facade-cognitoidentity" % version)
  }
  val awsLambdaJS = dep("net.exoego" %%% "aws-lambda-scalajs-facade" % "0.11.0")
}

object NpmDeps {
  val awsSdkVersion = "2.798.0"
  val awsSdk        = "aws-sdk" -> awsSdkVersion
}
