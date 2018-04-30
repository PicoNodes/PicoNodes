package picoide.proto

sealed trait DownloaderEvent

object DownloaderEvent {
  case class DownloadedBytecode(crc: Long) extends DownloaderEvent
}
