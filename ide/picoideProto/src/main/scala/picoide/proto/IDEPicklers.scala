package picoide.proto

import boopickle.Default._

object IDEPicklers {
  implicit val ideEventPickler =
    compositePickler[IDEEvent].addConcreteType[IDEEvent.AvailableNodes]
  implicit val ideCommandPickler =
    compositePickler[IDECommand].addConcreteType[IDECommand.ListNodes.type]
}
