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
  private val subscribers           = mutable.Set[ActorRef]()
  private val log                   = Logging(this)

  override def preStart(): Unit =
    NodeServer
      .start()
      .map(NodeRegistry.AddNode)
      .to(Sink.actorRef(self, onCompleteMessage = PoisonPill))
      .run()

  override def postStop(): Unit =
    materializer.shutdown()

  override def receive = {
    case AddNode(node) =>
      log.info(s"Adding node ${node.id}")
      Source.empty
        .via(node.flow)
        .to(Sink.onComplete(_ => self ! RemoveNode(node)))
        .run()
      nodes += node.id -> node
      subscribers.foreach(_ ! ListNodesNodeAdded(node))
    case RemoveNode(node) =>
      log.info(s"Removing node ${node.id}")
      nodes -= node.id
      subscribers.foreach(_ ! ListNodesNodeRemoved(node))
    case ListNodes =>
      sender ! ListNodesResponse(nodes.values.toSeq)
      subscribers += sender
    case ListNodesUnsubscribe(ref) =>
      subscribers -= sender
    case GetNode(id) =>
      sender ! GetNodeResponse(nodes.get(id))
  }
}

object NodeRegistry {
  def props: Props = Props[NodeRegistry]()

  sealed trait Command
  sealed trait Response

  case class AddNode(node: ProgrammerNode)            extends Command
  private case class RemoveNode(node: ProgrammerNode) extends Command

  case object ListNodes                                    extends Command
  case class ListNodesUnsubscribe(ref: ActorRef)           extends Command
  case class ListNodesResponse(nodes: Seq[ProgrammerNode]) extends Response
  case class ListNodesNodeAdded(node: ProgrammerNode)      extends Response
  case class ListNodesNodeRemoved(node: ProgrammerNode)    extends Response

  case class GetNode(id: UUID)                             extends Command
  case class GetNodeResponse(node: Option[ProgrammerNode]) extends Response
}
