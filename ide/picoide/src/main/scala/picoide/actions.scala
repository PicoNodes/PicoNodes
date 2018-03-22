package picoide

import diode.Action

object Actions {
  object CurrentFile {
    case class ReplaceLine(line: Int, newContent: String) extends Action
    case class SplitLine(line: Int, position: Int)        extends Action
  }
}
