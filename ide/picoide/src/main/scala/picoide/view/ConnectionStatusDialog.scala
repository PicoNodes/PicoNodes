package picoide.view

import akka.stream.scaladsl.SourceQueueWithComplete
import diode.data.{Failed, Pot}
import diode.react.ModelProxy
import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.html_<^._
import picoide.proto.IDECommand
import picoide.Actions

object ConnectionStatusDialog {
  val component =
    ScalaComponent
      .builder[ModelProxy[Pot[SourceQueueWithComplete[IDECommand]]]](
        "ConnectionStatusDialog")
      .render_P(model =>
        <.div(
          ^.className := "connection-status",
          "Connecting...".when(model().isPending),
          "Disconnected".when(model().isUnavailable),
          "Connection failed".when(model().isFailed),
          <.button(
            ^.onClick --> model.dispatchCB(
              Actions.CommandQueue.Update(Pot.empty)),
            "Reconnect"
          ).when(model().isEmpty || model().isFailed)
      ))
      .build
}
