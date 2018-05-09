package picoide.view

import diode.data.Pot
import diode.react.ModelProxy
import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.html_<^._
import picoide.proto.SourceFileRef

object FileList {
  val component =
    ScalaComponent
      .builder[ModelProxy[Pot[Seq[SourceFileRef]]]]("FileList")
      .render_P(
        files =>
          <.div(
            <.h2("Files"),
            "Loading...".when(files().isEmpty),
            <.ul(
              files().toSeq.flatten.map(file => <.li(<.a(file.name))): _*
            ).when(!files().isEmpty)
        ))
      .build
}
