def diode(subProject: String) = ProjectRef(
  uri(
    "https://github.com/PicoNodes/diode.git#b86426452e3b2f53630424800e18186689144632"),
  subProject
)
lazy val diodeReact = diode("diodeReact")

lazy val picoasm = crossProject
  .settings(
    libraryDependencies += "org.scala-lang.modules" %%% "scala-parser-combinators" % "1.1.0"
  )
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
    libraryDependencies ++= Seq(
      "io.suzaku" %%% "boopickle" % "1.3.0"
    )
  )
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
  .settings(
    libraryDependencies ++= Seq(
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
    Compile / npmDependencies ++= Seq(
      "react"             -> "16.2.0",
      "react-dom"         -> "16.2.0",
      "react-codemirror2" -> "4.2.1",
      "codemirror"        -> "5.36.0"
    ),
    Compile / npmDevDependencies ++= Seq(
      "sass-loader"                -> "6.0.7",
      "css-loader"                 -> "0.28.11",
      "style-loader"               -> "0.20.3",
      "node-sass"                  -> "4.7.2",
      "hard-source-webpack-plugin" -> "0.6.4"
    )
  )
  .dependsOn(picoasmJS, picoideProtoJS, diodeReact)

lazy val picoserver = project
  .enablePlugins(WebScalaJSBundlerPlugin)
  .settings(
    scalaJSProjects := Seq(picoide),
    Assets / pipelineStages := Seq(scalaJSDev),
    libraryDependencies ++= Seq(
      "org.webjars"       % "webjars-locator-core" % "0.35",
      "com.typesafe.akka" %% "akka-http"           % "10.1.0",
      "com.typesafe.akka" %% "akka-stream"         % "2.5.11"
    )
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

// Diode is still compiled using Scala 2.12.4
diode("diodeJS") / ensimeIgnoreScalaMismatch := true
diode("diodeJVM") / ensimeIgnoreScalaMismatch := true
diode("diodeDataJS") / ensimeIgnoreScalaMismatch := true
diode("diodeDataJVM") / ensimeIgnoreScalaMismatch := true
diode("diodeCoreJS") / ensimeIgnoreScalaMismatch := true
diode("diodeCoreJVM") / ensimeIgnoreScalaMismatch := true
diode("diodeDevtoolsJS") / ensimeIgnoreScalaMismatch := true
diode("diodeDevtoolsJVM") / ensimeIgnoreScalaMismatch := true
diode("root") / ensimeIgnoreScalaMismatch := true
diodeReact / ensimeIgnoreScalaMismatch := true

ThisBuild / scalafmtOnCompile := true
