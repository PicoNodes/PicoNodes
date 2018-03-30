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
import scala.concurrent.duration._

object IDEClient {
  val protocolPickler
    : BidiFlow[ByteBuffer, IDEEvent, IDECommand, ByteBuffer, NotUsed] =
    BidiFlow
      .fromFunctions(Unpickle[IDEEvent].fromBytes(_),
                     Pickle.intoBytes[IDECommand](_))
      .atop(
        BidiFlow.fromFlows(Flow[IDEEvent].log("protocolPickler.incoming"),
                           Flow[IDECommand].log("protocolPickler.outgoing")))
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
        .queue[IDECommand](10, OverflowStrategy.fail)
        .keepAlive(10.seconds, () => IDECommand.Ping)
        .viaMat(connect(url))(Keep.both)
        .wireTap(Sink.onComplete {
          case Failure(ex)   => Actions.CommandQueue.Update(Failed(ex))
          case Success(Done) => Actions.CommandQueue.Update(Unavailable)
        })
        .log("connectToCircuit.pre-dispatch")
        .to(Sink.foreach { msg =>
          try {
            circuit.dispatch(Actions.IDEEvent.Received(msg))
          } catch {
            case ex: Exception => ex.printStackTrace()
          }
        })
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
