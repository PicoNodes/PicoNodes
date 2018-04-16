package picoide.server.model

import akka.NotUsed
import akka.stream.scaladsl.Flow
import picoide.proto.{DownloaderCommand, DownloaderEvent, DownloaderInfo}

case class Downloader(info: DownloaderInfo,
                      flow: Flow[DownloaderCommand, DownloaderEvent, NotUsed]) {
  def id = info.id
}
