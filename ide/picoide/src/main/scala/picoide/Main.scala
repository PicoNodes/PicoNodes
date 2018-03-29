package picoide

import diode.data.Pot
import org.scalajs.dom.document
import picoide.view.EditorView
import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.html_<^._

object Main {
  def main(args: Array[String]): Unit = {
    Style.load()
    val circuit = new AppCircuit()
    circuit.dispatch(Actions.IDECommandQueue.Update(Pot.empty))
    circuit
      .wrap(identity(_))(model => EditorView.component(model))
      .renderIntoDOM(document.getElementById("main-container"))
  }
}
