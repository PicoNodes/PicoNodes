package picoide.proto

sealed trait DownloaderCommand

object DownloaderCommand {
  case object Ping                                 extends DownloaderCommand
  case class DownloadBytecode(bytecode: Seq[Byte]) extends DownloaderCommand
}
