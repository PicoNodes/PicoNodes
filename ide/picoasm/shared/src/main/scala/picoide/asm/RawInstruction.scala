package picoide.asm

case class RawInstruction(name: String,
                          opA: Option[RawOperand],
                          opB: Option[RawOperand],
                          flags: Flags) {
  override def toString() =
    f"$flags%-1s $name${opA.fold("")(" " + _)}${opB.fold("")(" " + _)}"
}

sealed trait RawOperand
object RawOperand {
  case class Integer(value: Int) extends RawOperand {
    override def toString() = value.toString()
  }
  case class Name(value: String) extends RawOperand {
    override def toString() = value.toString()
  }
}
