package picoide.server

import akka.NotUsed
import akka.pattern.{ask, pipe}
import akka.actor.ActorRef
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.stream.stage.OutHandler
import akka.stream.{Attributes, Outlet}
import akka.stream.scaladsl.{SinkQueueWithCancel, SourceQueueWithComplete}
import akka.stream.stage.{GraphStageLogic, InHandler, StageLogging}
import akka.stream.{FanOutShape, Inlet, Shape}
import akka.stream.stage.GraphStage
import akka.util.Timeout
import picoide.proto.{ProgrammerCommand, ProgrammerEvent}
import scala.concurrent.Future
import scala.concurrent.duration._
import picoide.proto.{IDECommand, IDEEvent, ProgrammerNodeInfo}
import cats.instances.option._
import cats.instances.future._
import cats.syntax.traverse._

class IDECommandHandlerShape(
    val in: Inlet[IDECommand],
    val out: Outlet[IDEEvent],
    val switchProgrammer: Outlet[
      Flow[ProgrammerCommand, ProgrammerEvent, NotUsed]])
    extends Shape {
  def this(name: String) = this(
    in = Inlet[IDECommand](s"$name.in"),
    out = Outlet[IDEEvent](s"$name.out"),
    switchProgrammer =
      Outlet[Flow[ProgrammerCommand, ProgrammerEvent, NotUsed]](
        s"$name.switchProgrammer")
  )

  override val inlets  = List(in)
  override val outlets = List(out, switchProgrammer)

  override def deepCopy(): IDECommandHandlerShape =
    new IDECommandHandlerShape(in = in.carbonCopy(),
                               out = out.carbonCopy(),
                               switchProgrammer = switchProgrammer.carbonCopy())
}

object IDECommandHandlerShape {
  def apply(name: String) = new IDECommandHandlerShape(name)
}

class IDECommandHandler(nodeRegistry: ActorRef)
    extends GraphStage[IDECommandHandlerShape] {
  override val shape = IDECommandHandlerShape("IDECommandHandler")
  import shape.{in, out, switchProgrammer}

  override def createLogic(inheritedAttributes: Attributes) =
    new GraphStageLogic(shape) with StageLogging {
      implicit def executionContext = materializer.executionContext

      case class SwapCurrentNode(node: Option[ProgrammerNode])

      override def preStart(): Unit = {
        def formatProgrammerNode(node: ProgrammerNode) =
          ProgrammerNodeInfo(node.id)
        getStageActor {
          case (_, NodeRegistry.ListNodesResponse(nodes)) =>
            push(out, IDEEvent.AvailableNodes(nodes.map(formatProgrammerNode)))
          case (_, NodeRegistry.ListNodesNodeAdded(node)) =>
            push(out, IDEEvent.AvailableNodeAdded(formatProgrammerNode(node)))
          case (_, NodeRegistry.ListNodesNodeRemoved(node)) =>
            push(out, IDEEvent.AvailableNodeRemoved(formatProgrammerNode(node)))
          case (_, SwapCurrentNode(node)) =>
            push(
              switchProgrammer,
              node
                .map(_.flow)
                .getOrElse(Flow.fromSinkAndSource(Sink.ignore, Source.empty)))
          case (sender, msg) =>
            log.warning(s"Unknown message $msg from $sender")
        }
        pull(in)
      }

      setHandler(
        in,
        new InHandler {
          override def onPush(): Unit = {
            implicit val timeout = Timeout(10.seconds)
            grab(in) match {
              case IDECommand.ListNodes =>
                nodeRegistry.tell(NodeRegistry.ListNodes, stageActor.ref)
                pull(in)
              case IDECommand.Ping =>
                push(out, IDEEvent.Pong)
                pull(in)
              case _: IDECommand.ToProgrammer =>
                pull(in)
              case IDECommand.SelectNode(node) =>
                node
                  .map(
                    id =>
                      (nodeRegistry ? NodeRegistry.GetNode(id))
                        .mapTo[NodeRegistry.GetNodeResponse]
                        .map(_.node))
                  .flatSequence
                  .map(SwapCurrentNode(_))
                  .pipeTo(stageActor.ref)
            }
          }
        }
      )

      setHandler(out, new OutHandler {
        override def onPull(): Unit = {}
      })

      setHandler(switchProgrammer, new OutHandler() {
        override def onPull(): Unit = {}
      })
    }
}

object IDECommandHandler {
  def apply(nodeRegistry: ActorRef) = new IDECommandHandler(nodeRegistry)
}
