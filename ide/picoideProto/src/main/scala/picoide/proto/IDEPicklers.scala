package picoide.proto

import boopickle.Default._

object IDEPicklers {
  implicit val ideEventPickler =
    compositePickler[IDEEvent]
      .addConcreteType[IDEEvent.AvailableNodes]
      .addConcreteType[IDEEvent.AvailableNodeAdded]
      .addConcreteType[IDEEvent.AvailableNodeRemoved]
      .addConcreteType[IDEEvent.Pong.type]
  implicit val ideCommandPickler =
    compositePickler[IDECommand]
      .addConcreteType[IDECommand.ListNodes.type]
      .addConcreteType[IDECommand.Ping.type]
      .addConcreteType[IDECommand.SelectNode]
}
