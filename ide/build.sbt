lazy val diodeReact = ProjectRef(
  uri(
    "https://github.com/PicoNodes/diode.git#b86426452e3b2f53630424800e18186689144632"),
  "diodeReact")

lazy val picoasm = crossProject
  .settings(
    libraryDependencies += "org.scala-lang.modules" %%% "scala-parser-combinators" % "1.1.0"
  )
lazy val picoasmJVM = picoasm.jvm
lazy val picoasmJS  = picoasm.js

lazy val picoide = project
  .enablePlugins(ScalaJSPlugin, ScalaJSBundlerPlugin)
  .settings(
    // libraryDependencies += "io.suzaku" %%% "diode-react" % "1.1.3",
    scalaJSUseMainModuleInitializer := true,
    scalaJSOutputMode := org.scalajs.core.tools.linker.standard.OutputMode.ECMAScript2015,
    scalaJSLinkerConfig ~= {
      _.withModuleKind(ModuleKind.CommonJSModule).withSourceMap(false)
    },
    webpackDevServerExtraArgs in fastOptJS ++= Seq(
      "--content-base",
      (sourceDirectory.value / "main" / "web").getAbsolutePath
    ),
    npmDependencies in Compile ++= Seq(
      "react"     -> "16.2.0",
      "react-dom" -> "16.2.0"
    )
  )
  .dependsOn(picoasmJS, diodeReact)

lazy val root = project
  .in(file("."))
  .aggregate(
    picoasmJVM,
    picoasmJS,
    picoide
  )

scalaVersion in ThisBuild := "2.12.5"

// Diode is still compiled using Scala 2.12.4
ensimeIgnoreScalaMismatch in diodeReact.copy(project = "diodeJS") := true
ensimeIgnoreScalaMismatch in diodeReact.copy(project = "diodeJVM") := true
ensimeIgnoreScalaMismatch in diodeReact.copy(project = "diodeDataJS") := true
ensimeIgnoreScalaMismatch in diodeReact.copy(project = "diodeDataJVM") := true
ensimeIgnoreScalaMismatch in diodeReact.copy(project = "diodeCoreJS") := true
ensimeIgnoreScalaMismatch in diodeReact.copy(project = "diodeCoreJVM") := true
ensimeIgnoreScalaMismatch in diodeReact.copy(project = "diodeDevtoolsJS") := true
ensimeIgnoreScalaMismatch in diodeReact.copy(project = "diodeDevtoolsJVM") := true
ensimeIgnoreScalaMismatch in diodeReact.copy(project = "root") := true
ensimeIgnoreScalaMismatch in diodeReact := true

scalafmtOnCompile in ThisBuild := true
