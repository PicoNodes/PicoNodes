package picoide

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.typesafe.config.ConfigFactory
import diode.data.Pot
import org.scalajs.dom.document
import picoide.view.EditorView
import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.html_<^._
import picoide.view.PicoASMMode

object Main {
  def main(args: Array[String]): Unit = {
    Style.load()
    PicoASMMode.register()

    val config = ConfigFactory
      .parseString("""akka {
                     |  loglevel = "DEBUG"
                     |  stdout-loglevel = "DEBUG"
                     |}
                     |""".stripMargin)
      .withFallback(akkajs.Config.default)

    implicit val actorSystem  = ActorSystem("picoide", config)
    implicit val materializer = ActorMaterializer()

    val circuit = new AppCircuit
    circuit.dispatch(Actions.CommandQueue.Update(Pot.empty))
    circuit
      .wrap(identity(_))(model => EditorView.component(model))
      .renderIntoDOM(document.getElementById("main-container"))
  }
}
