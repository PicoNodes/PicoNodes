package picoide.server

import akka.stream.scaladsl.Keep
import akka.{Done, NotUsed}
import akka.actor.{Actor, ActorRef}
import akka.http.scaladsl.model.ws.{BinaryMessage, Message}
import akka.stream.scaladsl.{Broadcast, Merge, Zip, ZipWith2}
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
import picoide.proto.IDEPicklers._
import boopickle.Default._
import scala.concurrent.Future

object IDEConnection {
  val protocolPickler
    : BidiFlow[ByteString, IDECommand, IDEEvent, ByteString, NotUsed] =
    BidiFlow
      .fromFunctions((bytes: ByteString) =>
                       Unpickle[IDECommand].fromBytes(bytes.asByteBuffer),
                     (event: IDEEvent) => ByteString(Pickle.intoBytes(event)))
      .atop(BidiFlow.fromFlows(Flow[IDECommand].log("protocolPickler.incoming"),
                               Flow[IDEEvent].log("protocolPickler.outgoing")))
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

  def repeatFirst[T]: Flow[T, T, NotUsed] =
    Flow.fromGraph(GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      val limitInput = builder.add(Flow[T].take(1))
      val merger     = builder.add(Merge[T](2, eagerComplete = false))
      val splitter   = builder.add(Broadcast[T](2))

      limitInput ~> merger
      merger ~> splitter
      splitter.detach ~> merger

      FlowShape(limitInput.in, splitter.out(1))
    })

  def webSocketHandler(nodeRegistry: ActorRef)(
      implicit mat: Materializer): Flow[Message, Message, NotUsed] = {
    val eventTargetActorSource = Source.actorRef[Any](10, OverflowStrategy.fail)
    val finishSink             = Sink.ignore

    binaryMessagesFlow
      .atop(protocolPickler)
      .join(Flow.fromGraph(
        GraphDSL.create(eventTargetActorSource, finishSink)(Keep.both) {
          implicit builder => (events, finish) =>
            import GraphDSL.Implicits._

            val eventTargetActor =
              builder.materializedValue
                .map(_._1)
                .via(repeatFirst)

            val finishFuture = builder.materializedValue.map(_._2)

            val commandsWithEventActor = builder.add(Zip[IDECommand, ActorRef])
            val router = builder.add(Flow[(IDECommand, ActorRef)].map {
              case (IDECommand.ListNodes, eventTargetActor) =>
                nodeRegistry.tell(NodeRegistry.ListNodes, eventTargetActor)
              case (IDECommand.Ping, eventTargetActor) =>
                eventTargetActor ! IDEEvent.Pong
            })
            val formattedEvents = builder.add(Flow[Any].map {
              case event: IDEEvent =>
                event
              case NodeRegistry.ListNodesResponse(nodes) =>
                IDEEvent.AvailableNodes(
                  nodes.map(node => ProgrammerNodeInfo(id = node.id)))
              case NodeRegistry.ListNodesNodeAdded(node) =>
                IDEEvent.AvailableNodeAdded(ProgrammerNodeInfo(id = node.id))
              case NodeRegistry.ListNodesNodeRemoved(node) =>
                IDEEvent.AvailableNodeRemoved(ProgrammerNodeInfo(id = node.id))
            })
            val coupler = builder.add(WatchFuture[Any, Done].named("coupler"))

            eventTargetActor ~> commandsWithEventActor.in1
            commandsWithEventActor.out ~> router ~> finish
            events ~> coupler.in0
            finishFuture ~> coupler.in1
            coupler.out ~> formattedEvents

            FlowShape(commandsWithEventActor.in0, formattedEvents.out)
        }))
  }
}
