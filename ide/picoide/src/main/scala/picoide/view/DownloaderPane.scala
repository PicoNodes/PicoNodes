package picoide.view

import diode.react.ModelProxy
import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.html_<^._
import picoide.{Actions, Root}

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
            .connect(state =>
              (state.commandQueue,
               state.downloaders.toOption.flatMap(_.current)))
            .apply { state =>
              val ((commandQueue, currentDownloader)) = state()
              <.button(
                "Reset",
                ^.disabled := commandQueue.isEmpty || currentDownloader.isEmpty,
                ^.onClick --> state.dispatchCB(Actions.Downloaders.Reset)
              )
            },
          model
            .connect(
              state =>
                DownloadButton.Props(
                  state.currentFile.map(_.value),
                  state.downloaders.toOption.flatMap(_.current),
                  state.commandQueue))
            .apply(DownloadButton.component(_))
      )
    )
    .build
}
