package picoide.net

import akka.stream.{BufferOverflowException, QueueOfferResult}
import akka.{Done, NotUsed}
import akka.actor.ActorRefFactory
import akka.stream.{Materializer, OverflowStrategy}
import akka.stream.scaladsl._
import diode.NoAction
import diode.data.Failed
import diode.{Action, ModelR, ModelRO}
import diode.data.{Pending, Unavailable}
import diode.react.ModelProxy
import diode.{Circuit, Effect}
import diode.data.{Pot, Ready}
import java.nio.ByteBuffer
import boopickle.Default._
import picoide.proto.{DownloaderInfo, IDECommand, IDEEvent}
import picoide.proto.IDEPicklers._
import picoide.Actions
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}
import scala.concurrent.duration._

object IDEClient {
  type CommandQueue = SourceQueueWithComplete[IDECommand]

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
        .log("connectToCircuit.outgoing")
        .viaMat(connect(url))(Keep.both)
        .log("connectToCircuit.incoming")
        .to(Sink.combine(
          Sink.foreach[IDEEvent] { msg =>
            try {
              circuit.dispatch(Actions.IDEEvent.Received(msg))
            } catch {
              case ex: Exception => ex.printStackTrace()
            }
          },
          Sink.onComplete {
            case Failure(ex) =>
              circuit.dispatch(Actions.CommandQueue.Update(Failed(ex)))
            case Success(Done) =>
              circuit.dispatch(Actions.CommandQueue.Update(Unavailable))
          }
        )(Broadcast(_)))
        .run()
      connectedFuture
        .map(_ => queue)
        .map(Ready(_))
        .map(Actions.CommandQueue.Update)
    }

  def requestDownloaderList(commandQueue: ModelRO[Pot[CommandQueue]])(
      implicit executionContext: ExecutionContext): Effect = Effect {
    commandQueue()
      .fold[Future[Pot[Set[DownloaderInfo]]]](Future.successful(Unavailable))(
        _.offer(IDECommand.ListDownloaders).map {
          case QueueOfferResult.Enqueued =>
            Pending()
          case QueueOfferResult.Dropped =>
            Failed(new BufferOverflowException("Request was dropped"))
          case QueueOfferResult.Failure(ex) =>
            Failed(ex)
          case QueueOfferResult.QueueClosed =>
            Unavailable
        })
      .map(Actions.Downloaders.Update(_))
  }

  def selectDownloader(downloader: Option[DownloaderInfo],
                       commandQueue: ModelRO[Pot[CommandQueue]])(
      implicit ec: ExecutionContext): Effect = Effect {
    commandQueue().get
      .offer(IDECommand.SelectDownloader(downloader.map(_.id)))
      .map(_ => NoAction)
  }
}
