package picoide.proto

import boopickle.Default._

object IDEPicklers {
  implicit val downloaderEventPickler   = compositePickler[DownloaderEvent]
  implicit val downloaderCommandPickler = compositePickler[DownloaderCommand]

  implicit val ideEventPickler =
    compositePickler[IDEEvent]
      .addConcreteType[IDEEvent.AvailableDownloaders]
      .addConcreteType[IDEEvent.AvailableDownloaderAdded]
      .addConcreteType[IDEEvent.AvailableDownloaderRemoved]
      .addConcreteType[IDEEvent.Pong.type]
      .addConcreteType[IDEEvent.FromDownloader]
  implicit val ideCommandPickler =
    compositePickler[IDECommand]
      .addConcreteType[IDECommand.ListDownloaders.type]
      .addConcreteType[IDECommand.Ping.type]
      .addConcreteType[IDECommand.SelectDownloader]
      .addConcreteType[IDECommand.ToDownloader]
}
