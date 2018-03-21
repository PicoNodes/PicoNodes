package picoide.asm

case class RawInstruction(name: String, opA: Option[RawOperand], opB: Option[RawOperand], flags: Flags)

sealed trait RawOperand
object RawOperand {
  case class Integer(value: Int) extends RawOperand
  case class Name(value: String) extends RawOperand
}
