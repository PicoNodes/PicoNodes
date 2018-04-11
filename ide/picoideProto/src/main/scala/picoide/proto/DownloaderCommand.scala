package picoide.proto

sealed trait DownloaderCommand

object DownloaderCommand {
  case object GetVersion                           extends DownloaderCommand
  case class DownloadBytecode(bytecode: Seq[Byte]) extends DownloaderCommand
}
