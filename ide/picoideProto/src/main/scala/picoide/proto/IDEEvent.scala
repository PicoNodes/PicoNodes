package picoide.proto

sealed trait IDEEvent
object IDEEvent {
  case class AvailableNodes(nodes: Seq[ProgrammerNodeInfo]) extends IDEEvent
}
