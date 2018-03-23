package picoide.view

import diode.react.ModelProxy
import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.html_<^._
import picoide.{Actions, AppCircuit, SourceFile}

object CodeEditor {
  case class LineProps(originalLine: String,
                       updateCallback: String => Callback,
                       splitCallback: Int => Callback)

  class LineBackend($ : BackendScope[LineProps, Option[String]]) {
    def updateLine(ev: ReactEventFromInput,
                   updateCallback: String => Callback) = {
      val value = ev.target.value
      $.setState(Some(value)) >> updateCallback(value)
    }

    def clearEdit(): Callback =
      $.setState(None)

    def onKeyPress(ev: ReactKeyboardEventFromInput,
                   splitCallback: Int => Callback): Callback =
      ev.key match {
        case "Enter" =>
          ev.preventDefaultCB >> splitCallback(ev.target.selectionStart)
        case _ =>
          Callback.empty
      }

    def render(props: LineProps, editedLine: Option[String]) =
      <.input(
        ^.value := editedLine.getOrElse(props.originalLine),
        ^.onChange ==> (updateLine(_, props.updateCallback)),
        ^.onBlur --> clearEdit(),
        ^.onKeyPress ==> (onKeyPress(_, props.splitCallback))
      )
  }

  val lineComponent =
    ScalaComponent
      .builder[LineProps]("CodeEditor.line")
      .initialState(None: Option[String])
      .renderBackend[LineBackend]
      .build

  class Backend($ : BackendScope[ModelProxy[SourceFile], Unit]) {
    def updateLine(lineNo: Int,
                   newLine: String,
                   modelProxy: ModelProxy[_]): Callback =
      modelProxy.dispatchCB(Actions.CurrentFile.ReplaceLine(lineNo, newLine))

    def splitLine(lineNo: Int,
                  position: Int,
                  modelProxy: ModelProxy[_]): Callback =
      modelProxy.dispatchCB(Actions.CurrentFile.SplitLine(lineNo, position))

    def render(file: ModelProxy[SourceFile]) =
      <.ol(
        ^.classSet("code-editor" -> true),
        TagMod(
          file.value.formatted.zipWithIndex.map {
            case ((line, lineNo)) =>
              <.li(
                lineComponent(
                  LineProps(line,
                            updateLine(lineNo, _, file),
                            splitLine(lineNo, _, file)))
              )
          }: _*
        )
      )
  }

  private val realComponent =
    ScalaComponent
      .builder[ModelProxy[SourceFile]]("CodeEditor")
      .renderBackend[Backend]
      .build
  val component = AppCircuit.connect(_.currentFile).apply(realComponent(_))
}
