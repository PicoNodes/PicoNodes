package picoide.asm

sealed trait Instruction {
  def name: String
  def opcode: Byte
  def opA: Operand
  def opB: Operand
  def flags: Flags

  def opcodeWithFlags: Byte =
    (opcode |
      (if (flags.plus) 1 << 7 else 0) |
      (if (flags.minus) 1 << 6 else 0)).toByte

  def assemble: Array[Byte] =
    Array[Byte](
      opcodeWithFlags,
      opA.value,
      opB.value
    )
}
object Instruction {
  case class Mov(opA: Operand.Value, opB: Operand.Register, flags: Flags)
      extends Instruction {
    val name   = "mov"
    val opcode = 0
  }

  def decode(raw: RawInstruction): Either[DecodeError, Instruction] =
    raw match {
      case RawInstruction("mov", None, _, flags) =>
        Left(DecodeError.OpRequired)
      case RawInstruction("mov", _, None, flags) =>
        Left(DecodeError.OpRequired)
      case RawInstruction("mov", Some(opARaw), Some(opBRaw), flags) =>
        for {
          opA <- Operand.Value.decode(opARaw)
          opB <- Operand.Register.decode(opBRaw)
        } yield Mov(opA, opB, flags)
      case RawInstruction("", None, None, flags) =>
        Right(Mov(Operand.Register.Null, Operand.Register.Null, flags))
      case _ =>
        Left(DecodeError.UnknownInstruction)
    }
}

sealed trait DecodeError
object DecodeError {
  case object UnknownInstruction extends DecodeError
  case object OpRequired        extends DecodeError
  case object OpType            extends DecodeError
  case object OpRange           extends DecodeError
  case object OpIllegalRegister extends DecodeError
}

case class Flags(plus: Boolean = false, minus: Boolean = false) {
  def |(other: Flags): Flags =
    Flags(plus = this.plus || other.plus, minus = this.minus || other.minus)

  override def toString() = Seq(
    if (plus) "+" else "",
    if (minus) "-" else ""
  ).mkString
}

sealed trait Operand {
  // FIXME Doesn't work for labels, refactor
  val value: Byte
}
object Operand {
  sealed trait Value extends Operand {
    val value: Byte
  }
  object Value {
    def decode(raw: RawOperand): Either[DecodeError, Value] =
      (Integer.decodeP orElse
        Register.decodeP).lift
        .apply(raw)
        .getOrElse(Left(DecodeError.OpType))
  }

  case class Integer(value: Byte) extends Value
  object Integer {
    def decodeP: PartialFunction[RawOperand, Either[DecodeError, Integer]] = {
      case RawOperand.Integer(value) if value >= -100 && value <= 100 =>
        Right(Integer(value.toByte))
      case RawOperand.Integer(_) =>
        Left(DecodeError.OpRange)
    }
  }

  sealed trait Register extends Value {
    val name: String
  }
  object Register {
    import scala.util.{Left => ELeft, Right => ERight}

    case object Up extends Register {
      val name  = "up"
      val value = -128
    }
    case object Down extends Register {
      val name  = "down"
      val value = -127
    }
    case object Left extends Register {
      val name  = "left"
      val value = -126
    }
    case object Right extends Register {
      val name  = "right"
      val value = -125
    }
    case object Acc extends Register {
      val name  = "acc"
      val value = 126
    }
    case object Null extends Register {
      val name  = "null"
      val value = 127
    }

    val all = Seq(
      Up,
      Down,
      Left,
      Right,
      Acc,
      Null
    ).map(reg => (reg.name, reg)).toMap

    def decodeP: PartialFunction[RawOperand, Either[DecodeError, Register]] = {
      case RawOperand.Name(name) =>
        all
          .get(name)
          .fold[Either[DecodeError, Register]](
            ELeft(DecodeError.OpIllegalRegister))(ERight(_))
    }

    def decode(raw: RawOperand): Either[DecodeError, Register] =
      decodeP.lift
        .apply(raw)
        .getOrElse(ELeft(DecodeError.OpType))
  }
}
