package picoide.net

import akka.NotUsed
import akka.actor.ActorRefFactory
import akka.stream.{Materializer, OverflowStrategy}
import akka.stream.scaladsl._
import diode.Effect
import diode.data.{Pot, Ready}
import java.nio.ByteBuffer
import boopickle.Default._
import picoide.proto.{IDECommand, IDEEvent}
import picoide.proto.IDEPicklers._
import picoide.{Actions, AppCircuit}
import scala.concurrent.ExecutionContext

object IDEClient {
  val protocolPickler
    : BidiFlow[ByteBuffer, IDEEvent, IDECommand, ByteBuffer, NotUsed] =
    BidiFlow
      .fromFunctions(Unpickle[IDEEvent].fromBytes(_),
                     Pickle.intoBytes[IDECommand](_))
      .named("protocolPickler")

  def connect(url: String)(
      implicit materializer: Materializer,
      actorFactory: ActorRefFactory): Flow[IDECommand, IDEEvent, NotUsed] =
    WSClient
      .connect(url, Seq("picoide"))
      .join(WSClient.binaryMessagesFlow)
      .join(protocolPickler)

  def connectToCircuit(url: String)(
      implicit materializer: Materializer,
      actorFactory: ActorRefFactory,
      executionContext: ExecutionContext): Effect =
    Effect.action {
      val queue = Source
        .queue(10, OverflowStrategy.fail)
        .via(connect(url))
        .to(Sink.foreach(msg =>
          AppCircuit.dispatch(Actions.IDEEvent.Received(msg))))
        .run()
      Actions.IDECommandQueue.Update(Ready(queue))
    }
}
