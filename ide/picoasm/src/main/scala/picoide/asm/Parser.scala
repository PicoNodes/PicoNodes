package picoide.asm

import scala.util.parsing.combinator.RegexParsers

class PicoAsmParser extends RegexParsers {
  override def skipWhitespace = false

  def whitespace: Parser[Unit] = " +".r ^^^ { () }
  def newline: Parser[Unit] = "\r?\n".r ^^^ { () }
  def number: Parser[Int] = "[0-9]+".r ^^ { _.toInt }
  def name: Parser[String] = "[a-z]+".r

  def flags: Parser[Flags] =
    ("+" ^^^ Flags(plus = true) |
       "-" ^^^ Flags(minus = true) |
       success(Flags())) <~ whitespace.?
  def operand: Parser[RawOperand] =
    number ^^ RawOperand.Integer | name ^^ RawOperand.Name
  def rawInstruction: Parser[RawInstruction] =
    flags ~ name ~ (whitespace ~> operand).? ~ (whitespace ~> operand).? ^^ {
      case flags ~ name ~ opA ~ opB =>
        RawInstruction(name, opA, opB, flags)
    }
  def instruction: Parser[Instruction] =
    rawInstruction.flatMap(raw =>
      Instruction.decode(raw).fold(error => failure(error.toString()), success)
    )

  def instructions: Parser[Seq[Instruction]] =
    repsep(instruction, newline) <~ newline.?
}
