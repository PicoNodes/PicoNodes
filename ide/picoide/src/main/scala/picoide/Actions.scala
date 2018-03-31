package picoide

import akka.stream.scaladsl.SourceQueueWithComplete
import diode.Action
import diode.data.{Pot, PotAction}
import picoide.proto.{IDECommand, IDEEvent, ProgrammerNodeInfo}

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

  object ProgrammerNodes {
    case class Update(potResult: Pot[Set[ProgrammerNodeInfo]])
        extends PotAction[Set[ProgrammerNodeInfo], Update] {
      def next(newResult: Pot[Set[ProgrammerNodeInfo]]) =
        Update(newResult)
    }

    case class Add(node: ProgrammerNodeInfo)    extends Action
    case class Remove(node: ProgrammerNodeInfo) extends Action

    case class Select(node: Option[ProgrammerNodeInfo]) extends Action
  }

  object CurrentFile {
    case class Modify(newContent: String) extends Action
  }
}
