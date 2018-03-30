package picoide.proto

sealed trait IDEEvent
object IDEEvent {
  case class AvailableNodes(nodes: Seq[ProgrammerNodeInfo]) extends IDEEvent

  /**
    * Response to [[IDECommand.Ping]]
    */
  case object Pong extends IDEEvent
}
