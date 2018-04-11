package picoide.proto

sealed trait DownloaderCommand

object DownloaderCommand {
  case object GetVersion extends DownloaderCommand
}
