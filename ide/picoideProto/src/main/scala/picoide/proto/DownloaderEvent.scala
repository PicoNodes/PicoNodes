package picoide.proto

sealed trait DownloaderEvent

object DownloaderEvent {
  case class Version(version: Int)              extends DownloaderEvent
  case class DownloadedBytecode(checksum: Long) extends DownloaderEvent
}
