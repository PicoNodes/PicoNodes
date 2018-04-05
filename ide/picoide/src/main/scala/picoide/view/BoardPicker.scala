package picoide.view

import diode.data.Pot
import diode.react.ModelProxy
import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.html_<^._
import picoide.{Actions, Downloaders, Root}
import picoide.proto.DownloaderInfo

object BoardPicker {
  val component =
    ScalaComponent
      .builder[ModelProxy[Pot[Downloaders]]]("BoardPicker")
      .render_P(
        model =>
          <.div(
            "Pick a downloader, any downloader:",
            <.select(
              ^.disabled := !model().isReady,
              ^.value := (model().toOption
                .flatMap(_.current.map(_.id.toString())))
                .getOrElse(""),
              ^.onChange ==> { ev: ReactEventFromInput =>
                model.dispatchCB(Actions.Downloaders.Select(
                  model().get.all.find(_.id.toString() == ev.target.value)))
              },
              <.option(s"Loading...").when(model().isPending),
              <.option(s"(None)"),
              TagMod(
                model()
                  .fold(Seq[DownloaderInfo]())(_.all.toSeq)
                  .map(downloader =>
                    <.option(^.value := downloader.id.toString(),
                             downloader.toString())): _*
              )
            )
        )
      )
      .build
}
