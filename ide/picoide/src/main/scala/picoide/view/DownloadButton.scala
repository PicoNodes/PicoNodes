package picoide.view

import akka.stream.scaladsl.SourceQueueWithComplete
import diode.data.Pot
import diode.react.ModelProxy
import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.html_<^._
import picoide.proto.{DownloaderInfo, SourceFile}
import picoide.{Actions}
import picoide.asm.PicoAsmParser
import picoide.proto.IDECommand

object DownloadButton {
  case class Props(source: Pot[SourceFile],
                   currentDownloader: Option[DownloaderInfo],
                   queue: Pot[SourceQueueWithComplete[IDECommand]])

  class Backend($ : BackendScope[ModelProxy[Props], Unit]) {
    private def parse(source: SourceFile) =
      PicoAsmParser.parseAll(PicoAsmParser.instructions, source.content)

    private def upload(props: ModelProxy[Props]): Callback =
      CallbackTo(parse(props().source.get).get)
        .map(Actions.Downloaders.SendInstructions(_))
        .flatMap(props.dispatchCB(_))

    def render(props: ModelProxy[Props]) =
      <.button(
        "Download",
        ^.disabled := !props().queue.isReady || props().currentDownloader.isEmpty || props().source.toOption
          .fold(true)(parse(_).isEmpty),
        ^.onClick --> upload(props)
      )
  }

  val component =
    ScalaComponent
      .builder[ModelProxy[Props]]("DownloadButton")
      .renderBackend[Backend]
      .build
}
