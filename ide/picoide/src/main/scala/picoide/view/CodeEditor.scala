package picoide.view

import diode.react.ModelProxy
import japgolly.scalajs.react._
import japgolly.scalajs.react.MonocleReact._
import japgolly.scalajs.react.vdom.html_<^._
import picoide.{Actions, AppCircuit, SourceFile}
import picoide.asm.PicoAsmFormatter
import monocle.macros.Lenses
import picoide.view.vendor.ReactCodeMirror

object CodeEditor {
  @Lenses
  case class State(currentCoord: ReactCodeMirror.Coord)

  class Backend($ : BackendScope[ModelProxy[SourceFile], State]) {
    private def beforeChange(editor: ReactCodeMirror.Editor,
                             changes: Seq[ReactCodeMirror.Change],
                             value: String): Callback =
      $.props.flatMap(_.dispatchCB(Actions.CurrentFile.Modify(value)))

    private def onCursorMove(editor: ReactCodeMirror.Editor,
                             coord: ReactCodeMirror.Coord): Callback =
      $.state
        .map(_.currentCoord)
        .map { oldCoord =>
          if (oldCoord.line != coord.line && !editor
                .somethingSelected()) {
            reformatLine(editor, oldCoord.line)
          }
        } >> $.setStateL(State.currentCoord)(coord)

    private def reformatLine(editor: ReactCodeMirror.Editor, line: Int): Unit =
      PicoAsmFormatter
        .formatInstruction(editor.getLine(line))
        .foreach { formatted =>
          editor.replaceRange(formatted,
                              ReactCodeMirror.coord(line, 0),
                              ReactCodeMirror.coord(line))
        }

    def render(file: ModelProxy[SourceFile]) =
      ReactCodeMirror.component(
        ReactCodeMirror.props(file().content,
                              onBeforeChange = beforeChange,
                              onCursor = onCursorMove))
  }

  private val realComponent =
    ScalaComponent
      .builder[ModelProxy[SourceFile]]("CodeEditor")
      .initialState(State(currentCoord = ReactCodeMirror.coord(line = 0)))
      .renderBackend[Backend]
      .build
  val component = AppCircuit.connect(_.currentFile).apply(realComponent(_))
}
