import sbtcrossproject.CrossPlugin.autoImport.{crossProject, CrossType}

Global / onChangedBuildSource := ReloadOnSourceChanges

inThisBuild(
  Seq(
    organization           := "io.github.fun-stack",
    scalaVersion           := "2.13.8",
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

lazy val commonSettings = Seq(
  addCompilerPlugin("org.typelevel" % "kind-projector" % "0.13.2" cross CrossVersion.full),
  libraryDependencies  ++=
    Deps.scalatest.value % Test ::
      Nil,
  scalacOptions --= Seq("-Xfatal-warnings", "-Wconf:any&src=src_managed/.*"),
)

lazy val jsSettings = Seq(
  useYarn       := true,
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
    name                 := "fun-stack-core",
    libraryDependencies ++=
      Deps.chameleon.value ::
        Nil,
  )

lazy val backend = project
  .enablePlugins(ScalaJSPlugin, ScalaJSBundlerPlugin)
  .in(file("backend"))
  .dependsOn(core, wsCore)
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
        Deps.tapir.redoc.value ::
        Deps.apiSpec.circe.value ::
        Nil,
  )

lazy val lambdaWsEventAuthorizer = project
  .enablePlugins(ScalaJSPlugin, ScalaJSBundlerPlugin)
  .dependsOn(core, wsCore)
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

lazy val web = project
  .enablePlugins(ScalaJSPlugin, ScalaJSBundlerPlugin)
  .dependsOn(core, wsCore)
  .in(file("web"))
  .settings(commonSettings, jsSettings)
  .settings(
    name                       := "fun-stack-web",
    libraryDependencies       ++=
      Deps.sloth.value ::
        Deps.cats.effect.value ::
        Deps.colibri.core.value ::
        Deps.colibri.jsdom.value ::
        Deps.mycelium.clientJs.value ::
        Nil,
    Compile / npmDependencies ++=
      NpmDeps.jwtDecode ::
        Nil,
  )

lazy val webTapir = project
  .enablePlugins(ScalaJSPlugin, ScalaJSBundlerPlugin)
  .dependsOn(core, web)
  .in(file("webTapir"))
  .settings(commonSettings, jsSettings)
  .settings(
    name                 := "fun-stack-web-tapir",
    libraryDependencies ++=
      Deps.tapir.jsClient.value ::
        Deps.tapir.catsClient.value ::
        Nil,
  )
