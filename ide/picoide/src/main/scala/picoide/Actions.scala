package picoide

import akka.stream.scaladsl.SourceQueueWithComplete
import diode.Action
import diode.data.{Pot, PotAction}
import picoide.proto.{DownloaderInfo, IDECommand, IDEEvent}

object Actions {
  object IDEEvent {
    case class Received(event: IDEEvent) extends Action
  }

  object CommandQueue {
    case class Update(potResult: Pot[SourceQueueWithComplete[IDECommand]])
        extends PotAction[SourceQueueWithComplete[IDECommand], Update] {
      def next(newResult: Pot[SourceQueueWithComplete[IDECommand]]) =
        Update(newResult)
    }
  }

  object Downloaders {
    case class Update(potResult: Pot[Set[DownloaderInfo]])
        extends PotAction[Set[DownloaderInfo], Update] {
      def next(newResult: Pot[Set[DownloaderInfo]]) =
        Update(newResult)
    }

    case class Add(downloader: DownloaderInfo)    extends Action
    case class Remove(downloader: DownloaderInfo) extends Action

    case class Select(downloader: Option[DownloaderInfo])   extends Action
    case class Selected(downloader: Option[DownloaderInfo]) extends Action
  }

  object CurrentFile {
    case class Modify(newContent: String) extends Action
  }
}
