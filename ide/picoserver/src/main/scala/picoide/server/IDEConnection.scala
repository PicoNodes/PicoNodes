package picoide.server

import akka.NotUsed
import akka.actor.{Actor, ActorRef}
import akka.http.scaladsl.model.ws.{BinaryMessage, Message}
import akka.stream.scaladsl.{Zip, ZipWith2}
import akka.stream.{FanOutShape, OverflowStrategy}
import akka.stream.stage.{InHandler, OutHandler}
import akka.stream.{Outlet, UniformFanOutShape}
import akka.stream.scaladsl.GraphDSL
import akka.stream.stage.GraphStageLogic
import akka.stream.{Attributes, FlowShape, Inlet, Materializer}
import akka.stream.scaladsl.{BidiFlow, Flow, Sink, Source}
import akka.stream.stage.GraphStage
import akka.util.ByteString
import java.nio.ByteBuffer
import picoide.proto.{IDECommand, IDEEvent, ProgrammerNodeInfo}
import boopickle.Default._

class IDEConnection(nodeRegistry: ActorRef) extends Actor {
  def receive = {
    case _ if false =>
  }
}

object IDEConnection {
  val protocolPickler
    : BidiFlow[ByteString, IDECommand, IDEEvent, ByteString, NotUsed] =
    BidiFlow
      .fromFunctions((bytes: ByteString) =>
                       Unpickle[IDECommand].fromBytes(bytes.asByteBuffer),
                     (event: IDEEvent) => ByteString(Pickle.intoBytes(event)))
      .named("protocolPickler")

  def readBinaryMessageFlow(
      implicit mat: Materializer): Flow[Message, ByteString, NotUsed] =
    Flow[Message]
      .map {
        case msg: BinaryMessage => msg.dataStream
        case _ =>
          throw new ClassCastException("Only binary messages are supported")
      }
      .mapAsync(1)(_.runFold(ByteString())(_ ++ _))
      .named("readBinaryMessageFlow")

  val writeBinaryMessageFlow: Flow[ByteString, Message, NotUsed] =
    Flow[ByteString].map(BinaryMessage(_)).named("writeBinaryMessageFlow")

  def binaryMessagesFlow(implicit mat: Materializer)
    : BidiFlow[Message, ByteString, ByteString, Message, NotUsed] =
    BidiFlow
      .fromFlows(readBinaryMessageFlow, writeBinaryMessageFlow)

  def webSocketHandler(nodeRegistry: ActorRef)(
      implicit mat: Materializer): Flow[Message, Message, NotUsed] =
    binaryMessagesFlow
      .atop(protocolPickler)
      .join(Flow.fromGraph(
        GraphDSL.create(Source.actorRef[Any](10, OverflowStrategy.fail)) {
          implicit builder => events =>
            import GraphDSL.Implicits._

            val eventTargetActor =
              builder.materializedValue.expand(Stream.continually(_).iterator)

            val commandsWithEventActor = builder.add(Zip[IDECommand, ActorRef])
            val router = builder.add(Sink.foreach[(IDECommand, ActorRef)] {
              case (IDECommand.ListNodes, eventTargetActor) =>
                nodeRegistry.tell(NodeRegistry.ListNodes, eventTargetActor)
            })
            val formattedEvents = builder.add(Flow[Any].map {
              case NodeRegistry.ListNodesResponse(nodes) =>
                IDEEvent.AvailableNodes(
                  nodes.map(node => ProgrammerNodeInfo(id = node.id)))
            })

            eventTargetActor ~> commandsWithEventActor.in1
            commandsWithEventActor.out ~> router

            events.out ~> formattedEvents

            FlowShape(commandsWithEventActor.in0, formattedEvents.out)
        }))
}
