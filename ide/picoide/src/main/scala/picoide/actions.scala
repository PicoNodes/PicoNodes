package picoide

import akka.stream.scaladsl.SourceQueueWithComplete
import diode.Action
import diode.data.{Pot, PotAction}
import picoide.proto.{IDECommand, IDEEvent}

object Actions {
  object IDEEvent {
    case class Received(event: IDEEvent) extends Action
  }

  object IDECommandQueue {
    case class Update(potResult: Pot[SourceQueueWithComplete[IDECommand]])
        extends PotAction[SourceQueueWithComplete[IDECommand], Update] {
      def next(newResult: Pot[SourceQueueWithComplete[IDECommand]]) =
        Update(newResult)
    }
  }

  object CurrentFile {
    case class Modify(newContent: String) extends Action
  }
}
