package picoide.proto

sealed trait IDEEvent
object IDEEvent {
  case class AvailableNodes(nodes: Seq[ProgrammerNodeInfo]) extends IDEEvent
  case class AvailableNodeAdded(node: ProgrammerNodeInfo)   extends IDEEvent
  case class AvailableNodeRemoved(node: ProgrammerNodeInfo) extends IDEEvent

  /**
    * Response to [[IDECommand.Ping]]
    */
  case object Pong extends IDEEvent

  case class FromProgrammer(event: ProgrammerEvent) extends IDEEvent
}
