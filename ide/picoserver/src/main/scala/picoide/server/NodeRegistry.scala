package picoide.server

import akka.NotUsed
import akka.actor.{ActorContext, ActorRef, Kill, PoisonPill, Props}
import akka.event.Logging
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Sink, Source}
import scala.collection.mutable
import akka.actor.Actor
import java.util.UUID

class NodeRegistry extends Actor {
  import NodeRegistry._
  import context._

  implicit private val materializer = ActorMaterializer.create(context)
  private val nodes                 = mutable.Map[UUID, ProgrammerNode]()
  private val log                   = Logging(this)

  override def preStart(): Unit =
    NodeServer
      .start()
      .map(NodeRegistry.AddNode)
      .to(
        Sink.actorRefWithAck(self,
                             onInitMessage = AddNodeNoop,
                             ackMessage = NodeAdded,
                             onCompleteMessage = PoisonPill,
                             onFailureMessage = _ => Kill))
      .run()

  override def receive = {
    case AddNode(node) =>
      log.info(s"Adding node ${node.id}")
      Source.empty
        .via(node.flow)
        .to(Sink.onComplete(_ => self ! RemoveNode(node)))
        .run()
      nodes += node.id -> node
      sender ! NodeAdded
    case AddNodeNoop =>
      sender ! NodeAdded
    case RemoveNode(node) =>
      log.info(s"Removing node ${node.id}")
      nodes -= node.id
    case ListNodes =>
      sender ! ListNodesResponse(nodes.values.toSeq)
    case GetNode(id) =>
      sender ! GetNodeResponse(nodes.get(id))
  }
}

object NodeRegistry {
  def props: Props = Props[NodeRegistry]()

  sealed trait Command
  sealed trait Response

  case class AddNode(node: ProgrammerNode) extends Command
  case object AddNodeNoop                  extends Response
  case object NodeAdded                    extends Response

  case class RemoveNode(node: ProgrammerNode) extends Command

  case object ListNodes                                    extends Command
  case class ListNodesResponse(nodes: Seq[ProgrammerNode]) extends Response

  case class GetNode(id: UUID)                             extends Command
  case class GetNodeResponse(node: Option[ProgrammerNode]) extends Response
}
