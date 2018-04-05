package picoide.proto

import boopickle.Default._

object IDEPicklers {
  implicit val programmerEventPickler   = compositePickler[ProgrammerEvent]
  implicit val programmerCommandPickler = compositePickler[ProgrammerCommand]

  implicit val ideEventPickler =
    compositePickler[IDEEvent]
      .addConcreteType[IDEEvent.AvailableNodes]
      .addConcreteType[IDEEvent.AvailableNodeAdded]
      .addConcreteType[IDEEvent.AvailableNodeRemoved]
      .addConcreteType[IDEEvent.Pong.type]
      .addConcreteType[IDEEvent.FromProgrammer]
  implicit val ideCommandPickler =
    compositePickler[IDECommand]
      .addConcreteType[IDECommand.ListNodes.type]
      .addConcreteType[IDECommand.Ping.type]
      .addConcreteType[IDECommand.SelectNode]
      .addConcreteType[IDECommand.ToProgrammer]
}
