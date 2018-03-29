package picoide

import diode.data.Pot
import org.scalajs.dom.document
import picoide.view.EditorView

object Main {
  def main(args: Array[String]): Unit = {
    Style.load()
    AppCircuit.dispatch(Actions.IDECommandQueue.Update(Pot.empty))
    EditorView.component
      .renderIntoDOM(document.getElementById("main-container"))
  }
}
