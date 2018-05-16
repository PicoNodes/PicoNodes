package picoide.view

import diode.data.Pot
import diode.react.ModelProxy
import japgolly.scalajs.react._
import japgolly.scalajs.react.MonocleReact._
import japgolly.scalajs.react.vdom.html_<^._
import picoide.Dirtying
import picoide.asm.PicoAsmParser
import picoide.proto.SourceFile
import picoide.{Actions}
import picoide.asm.PicoAsmFormatter
import monocle.macros.Lenses
import picoide.view.vendor.ReactCodeMirror
import scala.scalajs.js
import org.scalajs.dom

object CodeEditor {
  @Lenses
  case class State(currentCoord: ReactCodeMirror.Coord,
                   widgets: List[ReactCodeMirror.LineWidget] = List(),
                   renamingNewName: Option[String] = None)

  class Backend(
      $ : BackendScope[ModelProxy[Pot[Dirtying[SourceFile]]], State]) {

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
      editor
        .getLine(line)
        .toOption
        .flatMap(PicoAsmFormatter.formatInstruction)
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
        val parsed =
          value.lines
            .map(PicoAsmParser.parseAll(PicoAsmParser.instruction, _))
            .zipWithIndex
        // FIXME: Don't stop on the first error
        val errors = parsed.flatMap {
          case ((_: PicoAsmParser.Success[_], _)) =>
            None
          case ((err: PicoAsmParser.NoSuccess, line)) =>
            Some((err, line))
        }
        val errorWidgets = errors.map {
          case ((err, line)) =>
            val elem = dom.document.createElement("div")
            elem.textContent = err.msg
            elem.classList.add("picoasm-error")
            editor.addLineWidget(line, elem)
        }
        $.setStateL(State.widgets)(errorWidgets.toList)
    }

    def render(file: ModelProxy[Pot[Dirtying[SourceFile]]], state: State) =
      <.div(
        ^.classSet(
          "code-editor"       -> true,
          "code-editor-dirty" -> file().fold(false)(_.isDirty)
        ),
        <.div(
          ^.className := "code-editor-header",
          <.h2(
            file().fold(TagMod("(Loading...)"))(
              f =>
                state.renamingNewName.fold(
                  TagMod(f.value.ref.name,
                         ^.onClick --> $.setStateL(State.renamingNewName)(
                           Some(f.value.ref.name))))(newName =>
                  <.input(
                    ^.value := newName,
                    ^.onChange ==> ((ev: ReactEventFromInput) =>
                      $.setStateL(State.renamingNewName)(
                        Some(ev.target.value))),
                    ^.onBlur --> (file.dispatchCB(Actions.CurrentFile.Rename(
                      state.renamingNewName.get)) >> $.setStateL(
                      State.renamingNewName)(None)),
                    ^.autoFocus := true
                )))
          ),
          Spacer.component(),
          <.button(
            "Save",
            ^.disabled := file().isEmpty,
            ^.onClick --> file.dispatchCB(Actions.CurrentFile.Save)
          ),
          <.button("New",
                   ^.onClick --> file.dispatchCB(Actions.CurrentFile.CreateNew))
        ),
        ReactCodeMirror.component(
          ReactCodeMirror.props(file().fold("")(_.value.content),
                                onBeforeChange = beforeChange,
                                onChange = updateErrorWidgets,
                                onCursor = onCursorMove))
      )
  }

  val component =
    ScalaComponent
      .builder[ModelProxy[Pot[Dirtying[SourceFile]]]]("CodeEditor")
      .initialState(State(currentCoord = ReactCodeMirror.coord(line = 0)))
      .renderBackend[Backend]
      .build
}
