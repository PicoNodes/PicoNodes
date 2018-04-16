package picoide.server.model

import akka.NotUsed
import akka.actor.{ActorContext, ActorRef, Kill, PoisonPill, Props}
import akka.event.Logging
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Sink, Source}
import picoide.server.net.DownloaderServer
import scala.collection.mutable
import akka.actor.Actor
import java.util.UUID

class DownloaderRegistry extends Actor {
  import DownloaderRegistry._
  import context._

  implicit private val materializer = ActorMaterializer.create(context)
  private val downloaders           = mutable.Map[UUID, Downloader]()
  private val subscribers           = mutable.Set[ActorRef]()
  private val log                   = Logging(this)

  override def preStart(): Unit =
    DownloaderServer
      .start()
      .map(DownloaderRegistry.AddDownloader)
      .to(Sink.actorRef(self, onCompleteMessage = PoisonPill))
      .run()

  override def postStop(): Unit =
    materializer.shutdown()

  override def receive = {
    case AddDownloader(downloader) =>
      log.info(s"Adding downloader ${downloader.id}")
      Source.empty
        .via(downloader.flow)
        .to(Sink.onComplete(_ => self ! RemoveDownloader(downloader)))
        .run()
      downloaders += downloader.id -> downloader
      subscribers.foreach(_ ! ListDownloadersDownloaderAdded(downloader))
    case RemoveDownloader(downloader) =>
      log.info(s"Removing downloader ${downloader.id}")
      downloaders -= downloader.id
      subscribers.foreach(_ ! ListDownloadersDownloaderRemoved(downloader))
    case ListDownloaders =>
      sender ! ListDownloadersResponse(downloaders.values.toSeq)
      subscribers += sender
    case ListDownloadersUnsubscribe(ref) =>
      subscribers -= sender
    case GetDownloader(id) =>
      sender ! GetDownloaderResponse(downloaders.get(id))
  }
}

object DownloaderRegistry {
  def props: Props = Props[DownloaderRegistry]()

  sealed trait Command
  sealed trait Response

  case class AddDownloader(downloader: Downloader)            extends Command
  private case class RemoveDownloader(downloader: Downloader) extends Command

  case object ListDownloaders                          extends Command
  case class ListDownloadersUnsubscribe(ref: ActorRef) extends Command
  case class ListDownloadersResponse(downloaders: Seq[Downloader])
      extends Response
  case class ListDownloadersDownloaderAdded(downloader: Downloader)
      extends Response
  case class ListDownloadersDownloaderRemoved(downloader: Downloader)
      extends Response

  case class GetDownloader(id: UUID) extends Command
  case class GetDownloaderResponse(downloader: Option[Downloader])
      extends Response
}
