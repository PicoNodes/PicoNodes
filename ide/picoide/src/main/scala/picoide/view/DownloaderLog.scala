package picoide.view

import diode.react.ModelProxy
import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.html_<^._
import picoide.proto.DownloaderEvent

object DownloaderLog {
  val component = ScalaComponent
    .builder[ModelProxy[Seq[DownloaderEvent]]]("DownloaderLog")
    .render_P(
      events =>
        <.div(<.h1("Events"),
              <.ul(
                events()
                  .map(_.toString())
                  .map(<.li(_)): _*
              )))
    .build
}
