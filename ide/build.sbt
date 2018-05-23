def commonSettings = Seq(
  scalacOptions ++= Seq("-feature", "-deprecation")
)

lazy val picoasm = crossProject
  .settings(
    libraryDependencies += "org.scala-lang.modules" %%% "scala-parser-combinators" % "1.1.0"
  )
  .settings(commonSettings: _*)
  .jvmSettings(
    // For some reason neo-sbt-scalafmt does not normally format the shared src directory...
    Compile / scalafmt / sourceDirectories ++= CrossType.Full
      .sharedSrcDir(baseDirectory.value, "main")
  )
lazy val picoasmJVM = picoasm.jvm
lazy val picoasmJS  = picoasm.js

lazy val picoideProto = crossProject
  .crossType(CrossType.Pure)
  .settings(
    addCompilerPlugin(
      "org.scalamacros" %% "paradise" % "2.1.0" cross CrossVersion.full),
    libraryDependencies ++= Seq(
      "io.suzaku"                  %%% "boopickle"     % "1.3.0",
      "com.github.julien-truffaut" %%% "monocle-macro" % "1.5.0-cats"
    )
  )
  .settings(commonSettings: _*)
  .jsSettings(
    libraryDependencies ++= Seq(
      "org.akka-js" %%% "akkajsactorstream" % "1.2.5.11"
    )
  )
  .jvmSettings(
    // For some reason neo-sbt-scalafmt does not normally format the shared src directory...
    Compile / scalafmt / sourceDirectories ++= CrossType.Pure
      .sharedSrcDir(baseDirectory.value, "main"),
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-stream" % "2.5.11"
    )
  )
lazy val picoideProtoJVM = picoideProto.jvm
lazy val picoideProtoJS  = picoideProto.js

lazy val picoide = project
  .enablePlugins(ScalaJSPlugin, ScalaJSBundlerPlugin, ScalaJSWeb)
  .settings(commonSettings: _*)
  .settings(
    scalacOptions += "-P:scalajs:sjsDefinedByDefault",
    libraryDependencies ++= Seq(
      "io.suzaku"                         %%% "diode-react"       % "1.1.3.120",
      "com.github.japgolly.scalajs-react" %%% "ext-monocle-cats"  % "1.2.0",
      "com.github.julien-truffaut"        %%% "monocle-macro"     % "1.5.0-cats",
      "org.akka-js"                       %%% "akkajsactor"       % "1.2.5.11",
      "org.akka-js"                       %%% "akkajsactorstream" % "1.2.5.11"
    ),
    addCompilerPlugin(
      "org.scalamacros" %% "paradise" % "2.1.0" cross CrossVersion.full),
    webpackConfigFile := Some(
      baseDirectory.value / "scalajs.webpack.config.js"),
    scalaJSUseMainModuleInitializer := true,
    scalaJSOutputMode := org.scalajs.core.tools.linker.standard.OutputMode.ECMAScript2015,
    scalaJSLinkerConfig ~= {
      _.withModuleKind(ModuleKind.CommonJSModule)
    },
    webpackBundlingMode := BundlingMode.LibraryOnly(),
    webpackMonitoredDirectories += (Compile / resourceDirectory).value,
    fastOptJS / webpackDevServerExtraArgs ++= Seq(
      "--content-base",
      (sourceDirectory.value / "main" / "web").getAbsolutePath
    ),
    //useYarn := true,
    Compile / npmDependencies ++= Seq(
      "react"             -> "16.2.0",
      "react-dom"         -> "16.2.0",
      "react-codemirror2" -> "4.2.1",
      "codemirror"        -> "5.36.0"
    ),
    Compile / npmDevDependencies ++= Seq(
      "sass-loader"                -> "7.0.1",
      "css-loader"                 -> "0.28.11",
      "style-loader"               -> "0.21.0",
      "node-sass"                  -> "4.9.0",
      "hard-source-webpack-plugin" -> "0.6.7"
    )
  )
  .dependsOn(picoasmJS, picoideProtoJS)

lazy val picoserver = project
  .enablePlugins(WebScalaJSBundlerPlugin, FlywayPlugin)
  .settings(commonSettings: _*)
  .settings(
    scalacOptions += "-Ypartial-unification",
    scalaJSProjects := Seq(picoide),
    Assets / pipelineStages := Seq(scalaJSDev),
    libraryDependencies ++= Seq(
      "org.webjars"         % "webjars-locator-core" % "0.35",
      "com.typesafe.akka"   %% "akka-http"           % "10.1.0",
      "com.typesafe.akka"   %% "akka-stream"         % "2.5.11",
      "org.typelevel"       %% "cats-core"           % "1.1.0",
      "com.github.tminglei" %% "slick-pg"            % "0.16.1",
      "com.typesafe.slick"  %% "slick-hikaricp"      % "3.2.3",
      "ch.qos.logback"      % "logback-classic"      % "1.2.3",
      "org.cryptacular"     % "cryptacular"          % "1.2.1"
    ),
    addCompilerPlugin(
      "org.scalamacros" % "paradise" % "2.1.1" cross CrossVersion.full),
    flywayUrl := "jdbc:postgresql:///piconodes",
    flywayUser := "piconodes",
    flywayLocations := Seq("db/migration")
  )
  .dependsOn(picoideProtoJVM)

lazy val picoroot = project
  .in(file("."))
  .aggregate(
    picoasmJVM,
    picoasmJS,
    picoide,
    picoserver,
    picoideProtoJVM,
    picoideProtoJS
  )

ThisBuild / scalaVersion := "2.12.5"

// ThisBuild / ensimeRepositoryUrls += "https://oss.sonatype.org/content/repositories/snapshots/"
// ThisBuild / ensimeServerVersion := "3.0.0-SNAPSHOT"

ThisBuild / scalafmtOnCompile := true
