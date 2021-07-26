import sbtcrossproject.CrossPlugin.autoImport.{crossProject, CrossType}

inThisBuild(Seq(
  version := "0.1.0-SNAPSHOT",

  scalaVersion := "2.13.5",

  Global / onChangedBuildSource := ReloadOnSourceChanges
))

lazy val commonSettings = Seq(
  addCompilerPlugin("org.typelevel" % "kind-projector" % "0.11.3" cross CrossVersion.full),

  resolvers ++=
    ("jitpack" at "https://jitpack.io") ::
    Nil,

  libraryDependencies ++=
    Deps.scalatest.value ::
    Nil,

  scalacOptions --= Seq("-Xfatal-warnings"),
)

lazy val jsSettings = Seq(
  useYarn := true,
)

lazy val core = project
  .enablePlugins(ScalaJSPlugin, ScalaJSBundlerPlugin)
  .in(file("core"))
  .settings(commonSettings, jsSettings)
  .settings(
    name := "fun-stack-core",
    libraryDependencies ++=
      Deps.base64.value ::
      "com.github.cornerman.chameleon" %%% "chameleon" % "01426c2" ::
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
      Deps.jsTime.value ::
      Deps.sttp.jsClient.value ::
      Deps.sttp.catsClient.value ::
      Deps.mycelium.clientJs.value ::
      Deps.awsSdkJS.sts.value ::
      Deps.awsSdkJS.cognitoidentity.value ::
      Nil,

    Compile / npmDependencies ++=
      NpmDeps.awsSdk ::
      Nil
  )

lazy val root = project
  .in(file("."))
  .settings(
    publish / skip := true,
  )
  .aggregate(core, lambdaWs, lambdaHttp, web)
