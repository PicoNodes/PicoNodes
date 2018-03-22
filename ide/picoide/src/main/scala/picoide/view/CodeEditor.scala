package picoide.view

import diode.react.ModelProxy
import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.html_<^._
import picoide.{Actions, AppCircuit, SourceFile}

object CodeEditor {
  private def onChange(ev: ReactEventFromTextArea,
                       file: ModelProxy[SourceFile]): Callback =
    file.dispatchCB(Actions.EditCurrentFile(ev.target.value))

  private val realComponent =
    ScalaComponent
      .builder[ModelProxy[SourceFile]]("CodeEditor")
      .render_P((file: ModelProxy[SourceFile]) =>
        <.textarea(^.value := file().formatted,
                   ^.onChange ==> (onChange(_, file))))
      .build
  val component = AppCircuit.connect(_.currentFile).apply(realComponent(_))
}
