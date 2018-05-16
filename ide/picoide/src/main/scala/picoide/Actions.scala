package picoide

import akka.stream.scaladsl.SourceQueueWithComplete
import diode.{Action, ActionType}
import diode.data.{Pot, PotAction}
import picoide.asm.Instruction
import picoide.proto.{
  DownloaderEvent,
  DownloaderInfo,
  IDECommand,
  IDEEvent,
  SourceFile,
  SourceFileRef
}

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

    case class AddEvent(dlEvent: DownloaderEvent) extends Action

    case class SendInstructions(instructions: Seq[Instruction]) extends Action
  }

  object CurrentFile {
    case class Modify(newContent: String) extends Action
    case class Rename(newName: String)    extends Action
    case object CreateNew                 extends Action
    case object Save                      extends Action
    case class Load(file: SourceFileRef)  extends Action

    case class Saved(file: SourceFile)          extends Action
    case class Loaded(file: Option[SourceFile]) extends Action

    case class PromptSaveAndThen(next: Action) extends Action
    case object PromptSaveCancel               extends Action
    case object PromptSaveIgnore               extends Action
  }

  object KnownFiles {
    case class Update(potResult: Pot[Seq[SourceFileRef]])
        extends PotAction[Seq[SourceFileRef], Update] {
      def next(newResult: Pot[Seq[SourceFileRef]]) = Update(newResult)
    }
  }
}
