import org.portablescala.sbtplatformdeps.PlatformDepsPlugin.autoImport._
import sbt._

object Deps {
  import Def.{setting => s}

  // testing
  val scalatest = s("org.scalatest" %%% "scalatest" % "3.2.0")

  // core libraries
  val cats = new {
    val core   = s("org.typelevel" %%% "cats-core" % "2.1.1")
    val effect = s("org.typelevel" %%% "cats-effect" % "2.3.0")
  }

  // frp
  val colibri = s("com.github.cornerman.colibri" %%% "colibri" % "f118a37")

  // rpc
  val sloth = s("com.github.cornerman.sloth" %%% "sloth" % "0.3.0")

  // websocket connecitivity
  val mycelium = new {
    val version  = "2a7a14c"
    val core     = s("com.github.cornerman.mycelium" %%% "mycelium-core" % version)
    val clientJs = s("com.github.cornerman.mycelium" %%% "mycelium-client-js" % version)
  }

  // utils
  val base64 = s("com.github.marklister" %%% "base64" % "0.3.0")

  // js utils
  val jsTime = s("io.github.cquiroz" %%% "scala-java-time" % "2.2.0")

  // sttp
  val sttp = new {
    val version      = "0.18.0-M15"
    val core         = s("com.softwaremill.sttp.tapir" %%% "tapir-core" % version)
    val circe        = s("com.softwaremill.sttp.tapir" %%% "tapir-json-circe" % version)
    val openApi      = s("com.softwaremill.sttp.tapir" %% "tapir-openapi-docs" % version)
    val circeOpenApi = s("com.softwaremill.sttp.tapir" %% "tapir-openapi-circe-yaml" % version)
    val jsClient     = s("com.softwaremill.sttp.tapir" %%% "tapir-sttp-client" % version)
    val catsClient   = s("com.softwaremill.sttp.client3" %%% "catsce2" % "3.3.11")
  }

  // aws-sdk-js
  val awsSdkJS = new {
    val version         = s"0.32.0-v${NpmDeps.awsSdkVersion}"
    val lambda          = s("net.exoego" %%% "aws-sdk-scalajs-facade-lambda" % version)
    val sts             = s("net.exoego" %%% "aws-sdk-scalajs-facade-sts" % version)
    val cognitoidentity = s("net.exoego" %%% "aws-sdk-scalajs-facade-cognitoidentity" % version)
  }
  val awsLambdaJS = s("net.exoego" %%% "aws-lambda-scalajs-facade" % "0.11.0")
}

object NpmDeps {
  val awsSdkVersion = "2.798.0"
  val awsSdk        = "aws-sdk" -> awsSdkVersion
}
