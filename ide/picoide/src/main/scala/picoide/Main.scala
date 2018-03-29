package picoide

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import diode.data.Pot
import org.scalajs.dom.document
import picoide.view.EditorView
import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.html_<^._

object Main {
  def main(args: Array[String]): Unit = {
    Style.load()

    implicit val actorSystem  = ActorSystem("picoide")
    implicit val materializer = ActorMaterializer()

    val circuit = new AppCircuit
    circuit.dispatch(Actions.CommandQueue.Update(Pot.empty))
    circuit
      .wrap(identity(_))(model => EditorView.component(model))
      .renderIntoDOM(document.getElementById("main-container"))
  }
}
