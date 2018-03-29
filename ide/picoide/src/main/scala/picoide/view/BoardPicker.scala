package picoide.view

import diode.data.Pot
import diode.react.ModelProxy
import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.html_<^._
import picoide.{Actions, Root}
import picoide.proto.ProgrammerNodeInfo

object BoardPicker {
  val component =
    ScalaComponent
      .builder[ModelProxy[Root]]("BoardPicker")
      .render_P(
        model =>
          <.div(
            "Pick a node, any node:",
            <.select(
              ^.disabled := !model().programmerNodes.isReady,
              <.option(s"Loading...").when(model().programmerNodes.isPending),
              TagMod(
                model().programmerNodes
                  .getOrElse(Seq())
                  .map(node => <.option(node.toString())): _*
              )
            ),
            <.button("Refresh",
                     ^.disabled := !model().commandQueue.isReady,
                     ^.onClick --> (model.dispatchCB(
                       Actions.ProgrammerNodes.Update(Pot.empty))))
        )
      )
      .build
}
