package picoide.proto

import boopickle.Default._

object IDEPicklers {
  implicit val ideEventPickler =
    compositePickler[IDEEvent]
      .addConcreteType[IDEEvent.AvailableNodes]
      .addConcreteType[IDEEvent.Pong.type]
  implicit val ideCommandPickler =
    compositePickler[IDECommand]
      .addConcreteType[IDECommand.ListNodes.type]
      .addConcreteType[IDECommand.Ping.type]
}
