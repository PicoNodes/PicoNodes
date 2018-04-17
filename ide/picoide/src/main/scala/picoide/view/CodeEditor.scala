package picoide.view

import diode.react.ModelProxy
import japgolly.scalajs.react._
import japgolly.scalajs.react.MonocleReact._
import japgolly.scalajs.react.vdom.html_<^._
import picoide.asm.PicoAsmParser
import picoide.{Actions, SourceFile}
import picoide.asm.PicoAsmFormatter
import monocle.macros.Lenses
import picoide.view.vendor.ReactCodeMirror
import scala.scalajs.js
import org.scalajs.dom

object CodeEditor {
  @Lenses
  case class State(currentCoord: ReactCodeMirror.Coord,
                   widgets: Seq[ReactCodeMirror.LineWidget] = Seq())

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

    private def updateErrorWidgets(editor: ReactCodeMirror.Editor,
                                   changes: Seq[ReactCodeMirror.Change],
                                   value: String): Callback = $.state.flatMap {
      state =>
        state.widgets.foreach(_.clear())
        val parsed = PicoAsmParser.parseAll(PicoAsmParser.instructions, value)
        // FIXME: Don't stop on the first error
        val errors = parsed match {
          case _: PicoAsmParser.Success[_] =>
            None
          case err: PicoAsmParser.NoSuccess =>
            Some(err)
        }
        val errorWidgets = errors.map { err =>
          <.div("hi").render
          val elem = dom.document.createElement("div")
          elem.textContent = err.msg
          editor.addLineWidget(err.next.pos.line - 1, elem)
        }
        $.setStateL(State.widgets)(errorWidgets.toSeq)
    }

    def render(file: ModelProxy[SourceFile]) =
      ReactCodeMirror.component(
        ReactCodeMirror.props(file().content,
                              onBeforeChange = beforeChange,
                              onChange = updateErrorWidgets,
                              onCursor = onCursorMove))
  }

  val component =
    ScalaComponent
      .builder[ModelProxy[SourceFile]]("CodeEditor")
      .initialState(State(currentCoord = ReactCodeMirror.coord(line = 0)))
      .renderBackend[Backend]
      .build
}
