package picoide.view

import japgolly.scalajs.react._
import scala.scalajs.js.annotation.JSImport
import scala.scalajs.js

object ReactCodeMirror {
  @JSImport("react-codemirror2", "Controlled")
  @js.native
  object RawComponent extends js.Object

  @js.native
  trait Editor extends js.Object {
    def getCursor(start: String = "head"): Coord = js.native

    def somethingSelected(): Boolean = js.native

    def getLine(line: Int): String               = js.native
    def getRange(from: Coord, to: Coord): String = js.native
    def replaceRange(replacement: String, from: Coord, to: Coord): Unit =
      js.native
  }

  @js.native
  trait Change extends js.Object

  @js.native
  trait Coord extends js.Object {
    val line: Int           = js.native
    val ch: js.UndefOr[Int] = js.native
  }
  def coord(line: Int, ch: js.UndefOr[Int] = js.undefined): Coord =
    js.Dynamic.literal(line = line, ch = ch).asInstanceOf[Coord]

  type OnChange   = (Editor, Seq[Change], String) => Callback
  type OnChangeJS = js.Function3[Editor, js.Array[Change], String, Unit]

  type OnCursor   = (Editor, Coord) => Callback
  type OnCursorJS = js.Function2[Editor, Coord, Unit]

  @js.native
  trait Options extends js.Object {}

  def options(): Options =
    new js.Object().asInstanceOf[Options]

  @js.native
  trait Props extends js.Object {
    var value: String              = js.native
    var onBeforeChange: OnChangeJS = js.native
    var onChange: OnChangeJS       = js.native
    var onCursor: OnCursorJS       = js.native
    var options: Options           = js.native
  }

  def props(value: String,
            onBeforeChange: OnChange = (_, _, _) => Callback.empty,
            onChange: OnChange = (_, _, _) => Callback.empty,
            onCursor: OnCursor = (_, _) => Callback.empty,
            options: Options = this.options()): Props = {
    val props = new js.Object().asInstanceOf[Props]
    props.value = value
    props.onBeforeChange = onBeforeChange(_, _, _).runNow()
    props.onChange = onChange(_, _, _).runNow()
    props.onCursor = onCursor(_, _).runNow()
    props.options = options
    props
  }

  val component = JsComponent[Props, Children.None, Null](RawComponent)
}
