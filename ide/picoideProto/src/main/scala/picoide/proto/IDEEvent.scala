package picoide.proto

sealed trait IDEEvent
object IDEEvent {
  case class AvailableDownloaders(downloaders: Seq[DownloaderInfo])
      extends IDEEvent
  case class AvailableDownloaderAdded(downloader: DownloaderInfo)
      extends IDEEvent
  case class AvailableDownloaderRemoved(downloader: DownloaderInfo)
      extends IDEEvent

  /**
    * Response to [[IDECommand.Ping]]
    */
  case object Pong extends IDEEvent

  case class FromDownloader(event: DownloaderEvent) extends IDEEvent
}
