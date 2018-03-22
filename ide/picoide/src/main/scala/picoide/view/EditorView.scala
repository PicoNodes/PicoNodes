package picoide.view

import diode.react.ModelProxy
import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.html_<^._
import picoide.{Actions, AppCircuit, SourceFile}

object EditorView {
  val component =
    ScalaComponent
      .builder[Unit]("EditorView")
      .renderStatic(
        <.div(
          CodeEditor.component,
          BytecodeViewer.component
        ))
      .build
      .apply
}
