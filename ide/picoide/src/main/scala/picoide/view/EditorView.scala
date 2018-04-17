package picoide.view

import diode.react.ModelProxy
import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.html_<^._
import picoide.{Actions, Root, SourceFile}

object EditorView {
  val component =
    ScalaComponent
      .builder[ModelProxy[Root]]("EditorView")
      .render_P(model =>
        <.div(
          <.h1("PicoIDE"),
          model
            .connect(_.commandQueue)
            .apply(ConnectionStatusDialog.component(_)),
          DownloaderPane.component(model),
          model.connect(_.currentFile).apply(CodeEditor.component(_)),
          model.connect(_.currentFile).apply(BytecodeViewer.component(_)),
          model
            .connect(_.downloaders.map(_.events).getOrElse(Seq()))
            .apply(DownloaderLog.component(_))
      ))
      .build
}
