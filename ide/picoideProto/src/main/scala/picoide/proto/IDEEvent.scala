package picoide.proto

import java.util.UUID

sealed trait IDEEvent
object IDEEvent {
  case class AvailableDownloaders(downloaders: Seq[DownloaderInfo])
      extends IDEEvent
  case class AvailableDownloaderAdded(downloader: DownloaderInfo)
      extends IDEEvent
  case class AvailableDownloaderRemoved(downloader: DownloaderInfo)
      extends IDEEvent

  case class KnownFiles(files: Seq[SourceFileRef]) extends IDEEvent

  case class DownloaderSelected(downloader: Option[UUID]) extends IDEEvent

  /**
    * Response to [[IDECommand.Ping]]
    */
  case object Pong extends IDEEvent

  case class FromDownloader(event: DownloaderEvent) extends IDEEvent
}
