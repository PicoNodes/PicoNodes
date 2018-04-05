package picoide.proto

import java.util.UUID

sealed trait IDECommand
object IDECommand {
  case object ListDownloaders extends IDECommand
  case object Ping            extends IDECommand

  case class SelectDownloader(id: Option[UUID]) extends IDECommand

  case class ToDownloader(cmd: DownloaderCommand) extends IDECommand
}
