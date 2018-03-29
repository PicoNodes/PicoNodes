package picoide

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer

import diode.{ActionHandler, ActionResult, Circuit, Effect, NoAction}
import diode.data.{Pot, PotAction, PotState}
import diode.react.ReactConnector

import picoide.net.IDEClient

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class AppCircuit extends Circuit[Root] with ReactConnector[Root] {
  private implicit val actorSystem  = ActorSystem("picoide")
  private implicit val materializer = ActorMaterializer()

  override def initialModel = Root(
    currentFile = SourceFile("""  mov 1 up
                               |  mov 2 null
                               |  mov 3 acc
                               |
                               |+ mov 4 acc
                               |- mov 6 acc
                               |  mov 5 null
""".stripMargin),
    commandQueue = Pot.empty
  )

  override def actionHandler =
    composeHandlers(editorHandler, commandQueueHandler)

  def editorHandler = new ActionHandler(zoomTo(_.currentFile)) {
    override def handle = {
      case Actions.CurrentFile.Modify(newContent) =>
        updated(value.copy(content = newContent))
    }
  }

  def commandQueueHandler = new ActionHandler(zoomTo(_.commandQueue)) {
    override def handle = {
      case action: Actions.IDECommandQueue.Update =>
        action.handleWith(
          this,
          IDEClient.connectToCircuit("ws://localhost:8080/connect",
                                     AppCircuit.this))(PotAction.handler())
    }
  }
}
