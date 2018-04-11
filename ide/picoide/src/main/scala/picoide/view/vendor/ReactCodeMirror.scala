package picoide.view.vendor

import japgolly.scalajs.react._
import scala.scalajs.js.annotation.{JSImport, ScalaJSDefined}
import scala.scalajs.js.|
import scala.scalajs.js

object ReactCodeMirror {
  @JSImport("codemirror", JSImport.Default)
  @js.native
  object CodeMirror extends js.Object {

    /**
      * Requires Simple to be loaded
      */
    def defineSimpleMode(name: String,
                         states: js.Dictionary[SimpleState]): Unit = js.native
  }

  /**
    * Marker object that applies the add-on
    */
  @JSImport("codemirror/addon/mode/simple.js", JSImport.Default)
  @js.native
  object Simple extends js.Object

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

  type SimpleState = js.Array[SimpleRule]

  @ScalaJSDefined
  class SimpleRule(
      val regex: String | js.RegExp,
      val token: js.Array[String] | String,
      val sol: Boolean = false,
      val next: js.UndefOr[String] = js.undefined,
      val push: js.UndefOr[String] = js.undefined,
      val pop: Boolean = false
  ) extends js.Object

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

  type OnEditorMount = (Editor) => Callback
  type OnEditorMountJS =
    js.Function1[Editor, Unit]

  @js.native
  trait Options extends js.Object {
    var lineNumbers: Boolean = js.native
    var mode: String         = js.native
  }

  def options(lineNumbers: Boolean = true,
              mode: String = "picoasm"): Options = {
    val opts = new js.Object().asInstanceOf[Options]
    opts.lineNumbers = lineNumbers
    opts.mode = mode
    opts
  }

  @js.native
  trait Props extends js.Object {
    var value: String                   = js.native
    var onBeforeChange: OnChangeJS      = js.native
    var onChange: OnChangeJS            = js.native
    var onCursor: OnCursorJS            = js.native
    var editorDidMount: OnEditorMountJS = js.native
    var options: Options                = js.native
  }

  def props(value: String,
            onBeforeChange: OnChange = (_, _, _) => Callback.empty,
            onChange: OnChange = (_, _, _) => Callback.empty,
            onCursor: OnCursor = (_, _) => Callback.empty,
            onEditorMount: OnEditorMount = (_) => Callback.empty,
            options: Options = this.options()): Props = {
    val props = new js.Object().asInstanceOf[Props]
    props.value = value
    props.onBeforeChange = onBeforeChange(_, _, _).runNow()
    props.onChange = onChange(_, _, _).runNow()
    props.onCursor = onCursor(_, _).runNow()
    props.editorDidMount = onEditorMount(_).runNow()
    props.options = options
    props
  }

  val component = {
    JsComponent[Props, Children.None, Null](RawComponent)
  }
}
