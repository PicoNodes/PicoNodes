package picoide.view

import akka.stream.scaladsl.SourceQueueWithComplete
import diode.data.Pot
import diode.react.ModelProxy
import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.html_<^._
import picoide.proto.DownloaderInfo
import picoide.{Actions, SourceFile}
import picoide.asm.PicoAsmParser
import picoide.proto.IDECommand

object DownloadButton {
  case class Props(source: SourceFile,
                   currentDownloader: Option[DownloaderInfo],
                   queue: Pot[SourceQueueWithComplete[IDECommand]])

  class Backend($ : BackendScope[ModelProxy[Props], Unit]) {
    private def parse(source: SourceFile) =
      PicoAsmParser.parseAll(PicoAsmParser.instructions, source.content)

    private def upload(props: ModelProxy[Props]): Callback =
      CallbackTo(parse(props().source).get)
        .map(Actions.Downloaders.SendInstructions(_))
        .flatMap(props.dispatchCB(_))

    def render(props: ModelProxy[Props]) =
      <.button(
        "Download",
        ^.disabled := !props().queue.isReady || props().currentDownloader.isEmpty || parse(
          props().source).isEmpty,
        ^.onClick --> upload(props))
  }

  val component =
    ScalaComponent
      .builder[ModelProxy[Props]]("DownloadButton")
      .renderBackend[Backend]
      .build
}
