package picoide.net

import akka.stream.{BufferOverflowException, QueueOfferResult}
import akka.{Done, NotUsed}
import akka.actor.ActorRefFactory
import akka.stream.{Materializer, OverflowStrategy}
import akka.stream.scaladsl._
import diode.data.Failed
import diode.{Action, ModelR, ModelRO}
import diode.data.{Pending, Unavailable}
import diode.react.ModelProxy
import diode.{Circuit, Effect}
import diode.data.{Pot, Ready}
import java.nio.ByteBuffer
import boopickle.Default._
import picoide.proto.{IDECommand, IDEEvent, ProgrammerNodeInfo}
import picoide.proto.IDEPicklers._
import picoide.Actions
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

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

  def connectToCircuit(url: String, circuit: Circuit[_])(
      implicit materializer: Materializer,
      executionContext: ExecutionContext): Effect =
    Effect {
      val (queue, connectedFuture) = Source
        .queue(0, OverflowStrategy.fail)
        .viaMat(connect(url))(Keep.both)
        .wireTap(Sink.onComplete {
          case Failure(ex)   => Actions.CommandQueue.Update(Failed(ex))
          case Success(Done) => Actions.CommandQueue.Update(Unavailable)
        })
        .to(Sink.foreach(msg =>
          circuit.dispatch(Actions.IDEEvent.Received(msg))))
        .run()
      connectedFuture
        .map(_ => queue)
        .map(Ready(_))
        .map(Actions.CommandQueue.Update)
    }

  def requestNodeList(
      commandQueue: ModelRO[Pot[SourceQueueWithComplete[IDECommand]]])(
      implicit executionContext: ExecutionContext): Effect = Effect {
    commandQueue()
      .fold[Future[Pot[Seq[ProgrammerNodeInfo]]]](
        Future.successful(Unavailable))(_.offer(IDECommand.ListNodes).map {
        case QueueOfferResult.Enqueued =>
          Pending()
        case QueueOfferResult.Dropped =>
          Failed(new BufferOverflowException("Request was dropped"))
        case QueueOfferResult.Failure(ex) =>
          Failed(ex)
        case QueueOfferResult.QueueClosed =>
          Unavailable
      })
      .map(Actions.ProgrammerNodes.Update(_))
  }
}
