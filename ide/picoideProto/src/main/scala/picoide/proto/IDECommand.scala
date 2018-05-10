package picoide.proto

import java.util.UUID

sealed trait IDECommand
object IDECommand {
  case object ListDownloaders                     extends IDECommand
  case class SelectDownloader(id: Option[UUID])   extends IDECommand
  case class ToDownloader(cmd: DownloaderCommand) extends IDECommand

  case object Ping extends IDECommand

  case object ListFiles                   extends IDECommand
  case class SaveFile(file: SourceFile)   extends IDECommand
  case class GetFile(file: SourceFileRef) extends IDECommand
}
