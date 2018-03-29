package picoide.net

import akka.{Done, NotUsed}
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
import scala.concurrent.{ExecutionContext, Future}

object IDEClient {
  val protocolPickler
    : BidiFlow[ByteBuffer, IDEEvent, IDECommand, ByteBuffer, NotUsed] =
    BidiFlow
      .fromFunctions(Unpickle[IDEEvent].fromBytes(_),
                     Pickle.intoBytes[IDECommand](_))
      .named("protocolPickler")

  def connect(url: String): Flow[IDECommand, IDEEvent, Future[Done]] =
    WSClient
      .connect(url, Seq("picoide"))
      .join(WSClient.binaryMessagesFlow)
      .join(protocolPickler)

  def connectToCircuit(url: String)(
      implicit materializer: Materializer,
      executionContext: ExecutionContext): Effect =
    Effect {
      val (queue, connectedFuture) = Source
        .queue(10, OverflowStrategy.fail)
        .viaMat(connect(url))(Keep.both)
        .to(Sink.foreach(msg =>
          AppCircuit.dispatch(Actions.IDEEvent.Received(msg))))
        .run()
      connectedFuture
        .map(_ => queue)
        .map(Ready(_))
        .map(Actions.IDECommandQueue.Update)
    }
}
