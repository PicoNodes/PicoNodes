package picoide

import diode.Action

object Actions {
  object CurrentFile {
    case class Modify(newContent: String) extends Action
  }
}
