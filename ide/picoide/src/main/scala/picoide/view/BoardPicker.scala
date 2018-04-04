package picoide.view

import diode.data.Pot
import diode.react.ModelProxy
import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.html_<^._
import picoide.{Actions, ProgrammerNodes, Root}
import picoide.proto.ProgrammerNodeInfo

object BoardPicker {
  val component =
    ScalaComponent
      .builder[ModelProxy[Pot[ProgrammerNodes]]]("BoardPicker")
      .render_P(
        model =>
          <.div(
            "Pick a node, any node:",
            <.select(
              ^.disabled := !model().isReady,
              ^.value := (model().toOption
                .flatMap(_.current.map(_.id.toString())))
                .getOrElse(""),
              ^.onChange ==> { ev: ReactEventFromInput =>
                model.dispatchCB(Actions.ProgrammerNodes.Select(
                  model().get.all.find(_.id.toString() == ev.target.value)))
              },
              <.option(s"Loading...").when(model().isPending),
              <.option(s"(None)"),
              TagMod(
                model()
                  .fold(Seq[ProgrammerNodeInfo]())(_.all.toSeq)
                  .map(node =>
                    <.option(^.value := node.id.toString(), node.toString())): _*
              )
            )
        )
      )
      .build
}
