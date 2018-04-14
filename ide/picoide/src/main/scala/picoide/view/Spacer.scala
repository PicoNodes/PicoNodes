package picoide.view

import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.html_<^._

object Spacer {
  val component = ScalaComponent
    .builder[Unit]("Spacer")
    .render_(<.div(^.className := "spacer"))
    .build
}
