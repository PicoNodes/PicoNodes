package picoide.server.net

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
import picoide.proto.{
  DownloaderCommand,
  DownloaderEvent,
  DownloaderInfo,
  IDECommand,
  IDEEvent
}
import picoide.server.model.SourceFileManager
import picoide.server.utils.SwappableFlowAdapter
import picoide.server.model.IDECommandHandler
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

  def webSocketHandler(downloaderRegistry: ActorRef,
                       fileManager: SourceFileManager)(
      implicit mat: Materializer): Flow[Message, Message, NotUsed] =
    binaryMessagesFlow
      .atop(protocolPickler)
      .join(Flow.fromGraph(GraphDSL.create() { implicit builder =>
        import GraphDSL.Implicits._

        val commandHandler =
          builder.add(IDECommandHandler(downloaderRegistry, fileManager))
        val commandBroadcast = builder.add(Broadcast[IDECommand](2))
        val eventMerger      = builder.add(Merge[IDEEvent](2))
        val downloaderSwapper = builder.add(
          new SwappableFlowAdapter[DownloaderCommand, DownloaderEvent])

        // The identity maps are to make it easy to comment out the log statements
        val inLog =
          builder.add(
            Flow[IDECommand]
            // .log("webSocketHandler.incoming")
              .map(identity))
        val outLog =
          builder.add(
            Flow[IDEEvent]
            // .log("webSocketHandler.outgoing")
              .map(identity))

        inLog ~> commandBroadcast ~> commandHandler.in
        commandHandler.out.buffer(10, OverflowStrategy.fail) ~> eventMerger
        commandHandler.switchDownloader ~> downloaderSwapper.in1

        commandBroadcast ~> Flow[IDECommand]
          .collectType[IDECommand.ToDownloader]
          .map(_.cmd) ~> downloaderSwapper.in0
        downloaderSwapper.out ~> Flow[DownloaderEvent].map(
          IDEEvent.FromDownloader(_)) ~> eventMerger
        eventMerger ~> outLog

        FlowShape(inLog.in, outLog.out)
      }))
      .named("ide-connection")
}
