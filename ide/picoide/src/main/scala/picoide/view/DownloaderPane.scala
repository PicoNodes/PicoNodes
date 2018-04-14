package picoide.view

import diode.react.ModelProxy
import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.html_<^._
import picoide.Root

object DownloaderPane {
  val component = ScalaComponent
    .builder[ModelProxy[Root]]("DownloaderPane")
    .render_P(
      model =>
        <.div(
          ^.className := "downloader-pane",
          model.connect(_.downloaders).apply(DownloaderPicker.component(_)),
          Spacer.component(),
          model
            .connect(
              state =>
                DownloadButton.Props(
                  state.currentFile,
                  state.downloaders.toOption.flatMap(_.current),
                  state.commandQueue))
            .apply(DownloadButton.component(_))
      )
    )
    .build
}
