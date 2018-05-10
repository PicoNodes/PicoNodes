package picoide.proto

import boopickle.Default._

object IDEPicklers {
  implicit val downloaderEventPickler = compositePickler[DownloaderEvent]
    .addConcreteType[DownloaderEvent.DownloadedBytecode]
  implicit val downloaderCommandPickler = compositePickler[DownloaderCommand]
    .addConcreteType[DownloaderCommand.Ping.type]
    .addConcreteType[DownloaderCommand.DownloadBytecode]

  implicit val ideEventPickler =
    compositePickler[IDEEvent]
      .addConcreteType[IDEEvent.AvailableDownloaders]
      .addConcreteType[IDEEvent.AvailableDownloaderAdded]
      .addConcreteType[IDEEvent.AvailableDownloaderRemoved]
      .addConcreteType[IDEEvent.KnownFiles]
      .addConcreteType[IDEEvent.GotFile]
      .addConcreteType[IDEEvent.SavedFile]
      .addConcreteType[IDEEvent.Pong.type]
      .addConcreteType[IDEEvent.DownloaderSelected]
      .addConcreteType[IDEEvent.FromDownloader]
  implicit val ideCommandPickler =
    compositePickler[IDECommand]
      .addConcreteType[IDECommand.ListDownloaders.type]
      .addConcreteType[IDECommand.SelectDownloader]
      .addConcreteType[IDECommand.ToDownloader]
      .addConcreteType[IDECommand.Ping.type]
      .addConcreteType[IDECommand.ListFiles.type]
      .addConcreteType[IDECommand.SaveFile]
      .addConcreteType[IDECommand.GetFile]
}
