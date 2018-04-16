package picoide.server.model

import akka.NotUsed
import akka.pattern.{ask, pipe}
import akka.actor.ActorRef
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.stream.stage.OutHandler
import akka.stream.{Attributes, Outlet}
import akka.stream.scaladsl.{SinkQueueWithCancel, SourceQueueWithComplete}
import akka.stream.stage.{GraphStageLogic, InHandler, StageLogging}
import akka.stream.{FanOutShape, Inlet, Shape}
import akka.stream.stage.GraphStage
import akka.util.Timeout
import picoide.proto.{DownloaderCommand, DownloaderEvent}
import scala.concurrent.Future
import scala.concurrent.duration._
import picoide.proto.{DownloaderInfo, IDECommand, IDEEvent}
import cats.instances.option._
import cats.instances.future._
import cats.syntax.traverse._

class IDECommandHandlerShape(
    val in: Inlet[IDECommand],
    val out: Outlet[IDEEvent],
    val switchDownloader: Outlet[
      Flow[DownloaderCommand, DownloaderEvent, NotUsed]])
    extends Shape {
  def this(name: String) = this(
    in = Inlet[IDECommand](s"$name.in"),
    out = Outlet[IDEEvent](s"$name.out"),
    switchDownloader =
      Outlet[Flow[DownloaderCommand, DownloaderEvent, NotUsed]](
        s"$name.switchDownloader")
  )

  override val inlets  = List(in)
  override val outlets = List(out, switchDownloader)

  override def deepCopy(): IDECommandHandlerShape =
    new IDECommandHandlerShape(in = in.carbonCopy(),
                               out = out.carbonCopy(),
                               switchDownloader = switchDownloader.carbonCopy())
}

object IDECommandHandlerShape {
  def apply(name: String) = new IDECommandHandlerShape(name)
}

class IDECommandHandler(downloaderRegistry: ActorRef)
    extends GraphStage[IDECommandHandlerShape] {
  override val shape = IDECommandHandlerShape("IDECommandHandler")
  import shape.{in, out, switchDownloader}

  override def createLogic(inheritedAttributes: Attributes) =
    new GraphStageLogic(shape) with StageLogging {
      implicit def executionContext = materializer.executionContext

      case class SwapCurrentDownloader(downloader: Option[Downloader])

      override def preStart(): Unit = {
        def formatDownloader(downloader: Downloader) =
          DownloaderInfo(downloader.id)
        getStageActor {
          case (_, DownloaderRegistry.ListDownloadersResponse(downloaders)) =>
            push(
              out,
              IDEEvent.AvailableDownloaders(downloaders.map(formatDownloader)))
          case (
              _,
              DownloaderRegistry.ListDownloadersDownloaderAdded(downloader)) =>
            push(
              out,
              IDEEvent.AvailableDownloaderAdded(formatDownloader(downloader)))
          case (_,
                DownloaderRegistry.ListDownloadersDownloaderRemoved(
                  downloader)) =>
            push(
              out,
              IDEEvent.AvailableDownloaderRemoved(formatDownloader(downloader)))
          case (_, SwapCurrentDownloader(downloader)) =>
            push(
              switchDownloader,
              downloader
                .map(_.flow)
                .getOrElse(Flow.fromSinkAndSource(Sink.ignore, Source.empty)))
            push(out, IDEEvent.DownloaderSelected(downloader.map(_.id)))
          case (sender, msg) =>
            log.warning(s"Unknown message $msg from $sender")
        }
        pull(in)
      }

      setHandler(
        in,
        new InHandler {
          override def onPush(): Unit = {
            implicit val timeout = Timeout(10.seconds)
            grab(in) match {
              case IDECommand.ListDownloaders =>
                downloaderRegistry.tell(DownloaderRegistry.ListDownloaders,
                                        stageActor.ref)
              case IDECommand.Ping =>
                push(out, IDEEvent.Pong)
              case _: IDECommand.ToDownloader =>
              case IDECommand.SelectDownloader(downloader) =>
                downloader
                  .map(id =>
                    (downloaderRegistry ? DownloaderRegistry.GetDownloader(id))
                      .mapTo[DownloaderRegistry.GetDownloaderResponse]
                      .map(_.downloader))
                  .flatSequence
                  .map(SwapCurrentDownloader(_))
                  .pipeTo(stageActor.ref)
            }
            pull(in)
          }
        }
      )

      setHandler(out, new OutHandler {
        override def onPull(): Unit = {}
      })

      setHandler(switchDownloader, new OutHandler() {
        override def onPull(): Unit = {}
      })
    }
}

object IDECommandHandler {
  def apply(downloaderRegistry: ActorRef) =
    new IDECommandHandler(downloaderRegistry)
}
