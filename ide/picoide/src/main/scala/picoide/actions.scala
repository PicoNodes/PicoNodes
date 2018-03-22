package picoide

import diode.Action

object Actions {
  case class EditCurrentFile(newContent: String) extends Action
}
