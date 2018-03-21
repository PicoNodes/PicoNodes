lazy val picoasm = crossProject
  .settings(
    libraryDependencies += "org.scala-lang.modules" %%% "scala-parser-combinators" % "1.1.0"
  )
lazy val picoasmJVM = picoasm.jvm
lazy val picoasmJS = picoasm.js

lazy val picoide = project
  .enablePlugins(ScalaJSPlugin, ScalaJSBundlerPlugin)
  .settings(
    libraryDependencies += "io.suzaku" %%% "diode-react" % "1.1.3",
    scalaJSUseMainModuleInitializer := true,
    scalaJSLinkerConfig ~= { _.withModuleKind(ModuleKind.CommonJSModule) },
    webpackDevServerExtraArgs in fastOptJS ++= Seq(
      "--content-base",
      (sourceDirectory.value / "main" / "web").getAbsolutePath
    )
  )
  .dependsOn(picoasmJS)

lazy val root = project.in(file(".")).aggregate(
  picoasmJVM,
  picoasmJS,
  picoide
)

scalaVersion in ThisBuild := "2.12.5"
