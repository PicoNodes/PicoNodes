package picoide.proto

sealed trait IDECommand
object IDECommand {
  case object ListNodes extends IDECommand
}
