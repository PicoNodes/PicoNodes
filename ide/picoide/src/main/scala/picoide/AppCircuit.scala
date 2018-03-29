package picoide

import akka.stream.Materializer

import diode.{ActionHandler, ActionResult, Circuit, Effect, NoAction}
import diode.data.{Pot, PotAction, PotState}
import diode.react.ReactConnector

import picoide.net.IDEClient

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class AppCircuit(implicit materializer: Materializer)
    extends Circuit[Root]
    with ReactConnector[Root] {
  override def initialModel = Root(
    currentFile = SourceFile("""  mov 1 up
                               |  mov 2 null
                               |  mov 3 acc
                               |
                               |+ mov 4 acc
                               |- mov 6 acc
                               |  mov 5 null
""".stripMargin),
    commandQueue = Pot.empty,
    programmerNodes = Pot.empty
  )

  override def actionHandler =
    composeHandlers(editorHandler, commandQueueHandler, programmerNodesHandler)

  def editorHandler = new ActionHandler(zoomTo(_.currentFile)) {
    override def handle = {
      case Actions.CurrentFile.Modify(newContent) =>
        updated(value.copy(content = newContent))
    }
  }

  def commandQueueHandler = new ActionHandler(zoomTo(_.commandQueue)) {
    override def handle = {
      case action: Actions.CommandQueue.Update =>
        action.handleWith(
          this,
          IDEClient.connectToCircuit("ws://localhost:8080/connect",
                                     AppCircuit.this))(PotAction.handler())
    }
  }

  def programmerNodesHandler = new ActionHandler(zoomTo(_.programmerNodes)) {
    override def handle = {
      case action: Actions.ProgrammerNodes.Update =>
        action.handleWith(this,
                          IDEClient.requestNodeList(zoomTo(_.commandQueue)))(
          PotAction.handler())
    }
  }
}
