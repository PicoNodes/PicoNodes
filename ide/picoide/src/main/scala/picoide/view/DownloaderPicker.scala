package picoide.view

import diode.data.Pot
import diode.react.ModelProxy
import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.html_<^._
import java.util.UUID
import picoide.{Actions, Downloaders, Root}
import picoide.proto.DownloaderInfo

object DownloaderPicker {
  val component =
    ScalaComponent
      .builder[ModelProxy[Pot[Downloaders]]]("DownloaderPicker")
      .render_P(
        model =>
          <.div(
            "Current downloader:",
            <.select(
              ^.disabled := !model().isReady,
              ^.value := (model().toOption
                .flatMap(_.current.map(_.id.toString())))
                .getOrElse(""),
              ^.onChange ==> { ev: ReactEventFromInput =>
                model.dispatchCB(
                  Actions.Downloaders.Select(
                    Some(ev.target.value)
                      .filter(_ != "(None)")
                      .map(UUID.fromString)
                      .flatMap(model().get.all.get(_))))
              },
              <.option(s"Loading...").when(model().isPending),
              <.option(s"(None)"),
              TagMod(
                model()
                  .fold(Seq[DownloaderInfo]())(_.all.values.toSeq)
                  .map(downloader =>
                    <.option(^.value := downloader.id.toString(),
                             downloader.toString())): _*
              )
            )
        )
      )
      .build
}
