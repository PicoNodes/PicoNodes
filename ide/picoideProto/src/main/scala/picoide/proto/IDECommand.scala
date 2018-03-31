package picoide.proto

import java.util.UUID

sealed trait IDECommand
object IDECommand {
  case object ListNodes extends IDECommand
  case object Ping      extends IDECommand

  case class SelectNode(id: Option[UUID]) extends IDECommand
}
