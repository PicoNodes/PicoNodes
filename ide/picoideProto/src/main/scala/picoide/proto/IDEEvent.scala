package picoide.proto

sealed trait IDEEvent
object IDEEvent {
  case object Hello extends IDEEvent
}
