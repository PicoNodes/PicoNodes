package picoide.view

import diode.data.Pot
import diode.react.ModelProxy
import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.html_<^._
import picoide.{Actions, Dirtying}
import picoide.proto.SourceFile

import scala.scalajs.js
import org.scalajs.dom

object Modal {
  val component =
    ScalaComponent
      .builder[Unit]("Modal")
      .render_C(children =>
        <.div(^.className := "modal",
              <.div(^.className := "modal-content", children)))
      .build
}
