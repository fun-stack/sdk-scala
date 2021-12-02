import sbtcrossproject.CrossPlugin.autoImport.{crossProject, CrossType}

Global / onChangedBuildSource := ReloadOnSourceChanges

inThisBuild(Seq(
  organization := "io.github.fun-stack",
  scalaVersion := "2.13.7",

  licenses := Seq("MIT License" -> url("https://opensource.org/licenses/MIT")),

  homepage := Some(url("https://github.com/fun-stack/fun-stack-scala")),

  scmInfo := Some(ScmInfo(
    url("https://github.com/fun-stack/fun-stack-scala"),
    "scm:git:git@github.com:fun-stack/fun-stack-scala.git",
    Some("scm:git:git@github.com:fun-stack/fun-stack-scala.git"))
  ),

  pomExtra :=
    <developers>
      <developer>
        <id>jkaroff</id>
        <name>Johannes Karoff</name>
        <url>https://github.com/cornerman</url>
      </developer>
    </developers>,

  sonatypeCredentialHost := "s01.oss.sonatype.org",
  sonatypeRepository := "https://s01.oss.sonatype.org/service/local",
))

lazy val commonSettings = Seq(
  addCompilerPlugin("org.typelevel" % "kind-projector" % "0.13.2" cross CrossVersion.full),
  libraryDependencies ++=
    Deps.scalatest.value % Test ::
      Nil,
  scalacOptions --= Seq("-Xfatal-warnings", "-Wconf:any&src=src_managed/.*"),
)

lazy val jsSettings = Seq(
  useYarn := true,
  scalacOptions += {
    val githubRepo    = "fun-stack/fun-stack-scala"
    val local         = baseDirectory.value.toURI
    val subProjectDir = baseDirectory.value.getName
    val remote        = s"https://raw.githubusercontent.com/${githubRepo}/${git.gitHeadCommit.value.get}"
    s"-P:scalajs:mapSourceURI:$local->$remote/${subProjectDir}/"
  },
)

lazy val core = project
  .enablePlugins(ScalaJSPlugin, ScalaJSBundlerPlugin)
  .in(file("core"))
  .settings(commonSettings, jsSettings)
  .settings(
    name := "fun-stack-core",
    libraryDependencies ++=
      Deps.base64.value ::
        Deps.chameleon.value ::
        Nil,
  )

lazy val lambdaHttp = project
  .enablePlugins(ScalaJSPlugin, ScalaJSBundlerPlugin)
  .dependsOn(core)
  .in(file("lambdaHttp"))
  .settings(commonSettings, jsSettings)
  .settings(
    name := "fun-stack-lambda-http",
    libraryDependencies ++=
      Deps.cats.effect.value ::
        Deps.awsSdkJS.lambda.value ::
        Deps.awsLambdaJS.value ::
        Deps.sttp.core.value ::
        Deps.sttp.circe.value ::
        /* Deps.sttp.openApi.value :: */
        /* Deps.sttp.circeOpenApi.value :: */
        Nil,

    // The aws-sdk is provided in lambda environment.
    // Not depending on it explicitly makes the bundle size smaller.
    // But we do not know whether our facades are on the correct version.
    /* Compile / npmDependencies ++= */
    /*   NpmDeps.awsSdk :: */
    /*   Nil */
  )

lazy val lambdaWs = project
  .enablePlugins(ScalaJSPlugin, ScalaJSBundlerPlugin)
  .dependsOn(core)
  .in(file("lambdaWs"))
  .settings(commonSettings, jsSettings)
  .settings(
    name := "fun-stack-lambda-ws",
    libraryDependencies ++=
      Deps.sloth.value ::
        Deps.mycelium.core.value ::
        Deps.awsSdkJS.lambda.value ::
        Deps.awsLambdaJS.value ::
        Nil,

    // The aws-sdk is provided in lambda environment.
    // Not depending on it explicitly makes the bundle size smaller.
    // But we do not know whether our facades are on the correct version.
    /* Compile / npmDependencies ++= */
    /*   NpmDeps.awsSdk :: */
    /*   Nil */
  )

lazy val web = project
  .enablePlugins(ScalaJSPlugin, ScalaJSBundlerPlugin)
  .dependsOn(core)
  .in(file("web"))
  .settings(commonSettings, jsSettings)
  .settings(
    name := "fun-stack-web",
    libraryDependencies ++=
      Deps.sloth.value ::
        Deps.cats.effect.value ::
        Deps.colibri.value ::
        /* Deps.jsTime.value :: */
        Deps.sttp.jsClient.value ::
        Deps.sttp.catsClient.value ::
        Deps.mycelium.clientJs.value ::
        Nil,
    Compile / npmDependencies ++=
        Nil,
  )

lazy val root = project
  .in(file("."))
  .settings(
    publish / skip := true,
  )
  .aggregate(core, lambdaWs, lambdaHttp, web)
