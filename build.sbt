Global / onChangedBuildSource := ReloadOnSourceChanges

inThisBuild(
  Seq(
    organization           := "io.github.fun-stack",
    scalaVersion           := "2.13.16",
    licenses               := Seq("MIT License" -> url("https://opensource.org/licenses/MIT")),
    homepage               := Some(url("https://github.com/fun-stack/fun-stack-scala")),
    scmInfo                := Some(
      ScmInfo(
        url("https://github.com/fun-stack/fun-stack-scala"),
        "scm:git:git@github.com:fun-stack/fun-stack-scala.git",
        Some("scm:git:git@github.com:fun-stack/fun-stack-scala.git"),
      ),
    ),
    pomExtra               :=
      <developers>
      <developer>
        <id>jkaroff</id>
        <name>Johannes Karoff</name>
        <url>https://github.com/cornerman</url>
      </developer>
    </developers>,
    sonatypeCredentialHost := "s01.oss.sonatype.org",
    sonatypeRepository     := "https://s01.oss.sonatype.org/service/local",
  ),
)

val isDotty = Def.setting(CrossVersion.partialVersion(scalaVersion.value).exists(_._1 == 3))

lazy val commonSettings = Seq(
  libraryDependencies  ++=
    Deps.scalatest.value % Test ::
      Nil,
  scalacOptions --= Seq("-Wconf:any&src=src_managed/.*"),
  libraryDependencies  ++= (if (isDotty.value) Nil
                           else
                             Seq(
                               compilerPlugin(("org.typelevel" %% "kind-projector" % "0.13.3").cross(CrossVersion.full)),
                             )),
)

lazy val jsSettings = Seq(
  useYarn := true,
)

lazy val core = project
  .enablePlugins(ScalaJSPlugin, ScalaJSBundlerPlugin)
  .in(file("core"))
  .settings(commonSettings, jsSettings)
  .settings(
    name                 := "fun-stack-core",
    libraryDependencies ++=
      Deps.chameleon.value ::
        Nil,
  )

lazy val backend = project
  .enablePlugins(ScalaJSPlugin, ScalaJSBundlerPlugin)
  .in(file("backend"))
  .dependsOn(wsCore)
  .settings(commonSettings, jsSettings)
  .settings(
    name                 := "fun-stack-backend",
    libraryDependencies ++=
      Deps.cats.effect.value ::
        Deps.awsSdkJS.sns.value ::
        Deps.awsSdkJS.cognitoidentityprovider.value ::
        Deps.sloth.value ::
        Deps.mycelium.core.value ::
        Nil,
  )

lazy val lambdaApigateway = project
  .enablePlugins(ScalaJSPlugin, ScalaJSBundlerPlugin)
  .in(file("lambdaApigateway"))
  .settings(commonSettings, jsSettings)
  .settings(
    name                 := "fun-stack-lambda-apigateway",
    libraryDependencies ++=
      Deps.cats.effect.value ::
        Deps.awsLambdaJS.value ::
        Nil,
  )

lazy val wsCore = project
  .enablePlugins(ScalaJSPlugin, ScalaJSBundlerPlugin)
  .dependsOn(core)
  .in(file("wsCore"))
  .settings(commonSettings, jsSettings)
  .settings(
    name                 := "fun-stack-ws-core",
    libraryDependencies ++=
      Deps.mycelium.core.value ::
        Nil,
  )

lazy val lambdaHttpRpc = project
  .enablePlugins(ScalaJSPlugin, ScalaJSBundlerPlugin)
  .dependsOn(core, lambdaApigateway)
  .in(file("lambdaHttpRpc"))
  .settings(commonSettings, jsSettings)
  .settings(
    name                 := "fun-stack-lambda-http-rpc",
    libraryDependencies ++=
      Deps.sloth.value ::
        Nil,
  )

lazy val lambdaWsRpc = project
  .enablePlugins(ScalaJSPlugin, ScalaJSBundlerPlugin)
  .dependsOn(core, lambdaApigateway, wsCore)
  .in(file("lambdaWsRpc"))
  .settings(commonSettings, jsSettings)
  .settings(
    name                 := "fun-stack-lambda-ws-rpc",
    libraryDependencies ++=
      Deps.sloth.value ::
        Deps.cats.effect.value ::
        Nil,
  )

lazy val lambdaHttpApiTapir = project
  .enablePlugins(ScalaJSPlugin, ScalaJSBundlerPlugin)
  .dependsOn(core, lambdaApigateway)
  .in(file("lambdaHttpApiTapir"))
  .settings(commonSettings, jsSettings)
  .settings(
    name                 := "fun-stack-lambda-http-api-tapir",
    libraryDependencies ++=
      Deps.tapir.core.value ::
        Deps.tapir.circe.value ::
        Deps.tapir.catsClient.value ::
        Deps.tapir.lambda.value ::
        Deps.tapir.openApi.value ::
        Deps.apiSpec.circe.value ::
        Nil,
  )

lazy val lambdaWsEventAuthorizer = project
  .enablePlugins(ScalaJSPlugin, ScalaJSBundlerPlugin)
  .dependsOn(wsCore)
  .in(file("lambdaWsEventAuthorizer"))
  .settings(commonSettings, jsSettings)
  .settings(
    name                 := "fun-stack-lambda-ws-event-authorizer",
    libraryDependencies ++=
      Deps.cats.effect.value ::
        Deps.awsSdkJS.sns.value ::
        Deps.awsLambdaJS.value ::
        Deps.sloth.value ::
        Nil,
  )

lazy val clientCore = project
  .enablePlugins(ScalaJSPlugin, ScalaJSBundlerPlugin)
  .dependsOn(wsCore)
  .in(file("clientCore"))
  .settings(commonSettings, jsSettings)
  .settings(
    name                       := "fun-stack-client-core",
    libraryDependencies       ++=
      Deps.sloth.value ::
        Deps.cats.effect.value ::
        Deps.colibri.core.value ::
        Deps.colibri.jsdom.value ::
        Deps.mycelium.clientJs.value ::
        Deps.tapir.jsClient.value ::
        Deps.tapir.catsClient.value ::
        Nil,
    Compile / npmDependencies ++=
      NpmDeps.jwtDecode ::
        Nil,
  )

lazy val clientNode = project
  .enablePlugins(ScalaJSPlugin, ScalaJSBundlerPlugin)
  .dependsOn(clientCore)
  .in(file("clientNode"))
  .settings(commonSettings, jsSettings)
  .settings(
    name                       := "fun-stack-client-node",
    libraryDependencies       ++=
      Deps.awsSdkJS.cognitoidentityprovider.value ::
        Nil,
    Compile / npmDependencies ++=
      NpmDeps.nodeFetch ::
        NpmDeps.ws ::
        NpmDeps.open ::
        Nil,
  )

lazy val clientWeb = project
  .enablePlugins(ScalaJSPlugin, ScalaJSBundlerPlugin)
  .dependsOn(clientCore)
  .in(file("clientWeb"))
  .settings(commonSettings, jsSettings)
  .settings(
    name                       := "fun-stack-client-web",
    libraryDependencies       ++=
      Nil,
    Compile / npmDependencies ++=
      Nil,
  )
