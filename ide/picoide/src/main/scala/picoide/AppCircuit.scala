package picoide

import akka.stream.Materializer
import diode.Action
import diode.data.Ready

import diode.{ActionHandler, ActionResult, Circuit, Effect, NoAction}
import diode.data.{Pot, PotAction, PotState}
import diode.react.ReactConnector

import picoide.net.IDEClient
import picoide.proto.{IDEEvent, ProgrammerNodeInfo}

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
    composeHandlers(editorHandler,
                    commandQueueHandler,
                    programmerNodesHandler,
                    ideEventHandler)

  def editorHandler = new ActionHandler(zoomTo(_.currentFile)) {
    override def handle = {
      case Actions.CurrentFile.Modify(newContent) =>
        updated(value.copy(content = newContent))
    }
  }

  def commandQueueHandler = new ActionHandler(zoomTo(_.commandQueue)) {
    override def handle = {
      case action: Actions.CommandQueue.Update =>
        import PotState._
        action.handle {
          case PotEmpty =>
            updated(value.pending(),
                    IDEClient.connectToCircuit("ws://localhost:8080/connect",
                                               AppCircuit.this))
          case PotPending =>
            noChange
          case PotReady =>
            updated(action.potResult,
                    Effect.action(Actions.ProgrammerNodes.Update(Pot.empty)))
          case PotUnavailable =>
            updated(value.unavailable())
          case PotFailed =>
            updated(value.fail(action.result.failed.get))
        }
    }
  }

  def programmerNodesHandler =
    new ActionHandler[Root, Pot[Set[ProgrammerNodeInfo]]](
      zoomTo(_.programmerNodes)) {
      override def handle: PartialFunction[Any, ActionResult[Root]] = {
        case action: Actions.ProgrammerNodes.Update =>
          action.handleWith(this,
                            IDEClient.requestNodeList(zoomTo(_.commandQueue)))(
            PotAction.handler())
        case Actions.ProgrammerNodes.Add(node) =>
          updated(value.map(_ + node))
        case Actions.ProgrammerNodes.Remove(node) =>
          updated(value.map(_ - node))
      }
    }

  def ideEventHandler =
    new ActionHandler(zoomRW(identity(_))((_, x) => x)) {
      def toAction(event: IDEEvent): Action = event match {
        case IDEEvent.AvailableNodes(nodes) =>
          Actions.ProgrammerNodes.Update(Ready(nodes.toSet))
        case IDEEvent.AvailableNodeAdded(node) =>
          Actions.ProgrammerNodes.Add(node)
        case IDEEvent.AvailableNodeRemoved(node) =>
          Actions.ProgrammerNodes.Remove(node)
        case IDEEvent.Pong =>
          NoAction
      }

      override def handle = {
        case Actions.IDEEvent.Received(event) =>
          effectOnly(Effect.action(toAction(event)))
      }
    }
}
