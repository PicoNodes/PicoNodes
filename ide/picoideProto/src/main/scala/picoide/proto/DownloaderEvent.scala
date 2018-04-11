package picoide.proto

sealed trait DownloaderEvent

object DownloaderEvent {
  case class Version(version: Int) extends DownloaderEvent
}
