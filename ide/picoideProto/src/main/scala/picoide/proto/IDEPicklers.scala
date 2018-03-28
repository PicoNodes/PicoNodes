package picoide.proto

import boopickle.Default._

object IDEPicklers {
  implicit val ideEventPickler   = compositePickler[IDEEvent]
  implicit val ideCommandPickler = compositePickler[IDECommand]
}
