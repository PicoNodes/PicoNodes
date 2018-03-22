package picoide.asm

import scala.util.parsing.combinator.RegexParsers

class PicoAsmParser extends RegexParsers {
  override val whiteSpace = """[ \t]+""".r

  def comment: Parser[String]  = "#([^\r\n])*".r
  def newline: Parser[Unit]    = "\r?\n".r ^^^ { () }
  def number: Parser[Int]      = "[0-9]+".r ^^ { _.toInt }
  def name: Parser[String]     = "[a-z]+".r

  def flags: Parser[Flags] =
    ("+" ^^^ Flags(plus = true) |
      "-" ^^^ Flags(minus = true) |
      success(Flags()))
  def operand: Parser[RawOperand] =
    number ^^ RawOperand.Integer | name ^^ RawOperand.Name
  def rawInstruction: Parser[RawInstruction] =
    flags ~ name.? ~ (operand).? ~ (operand).? ^^ {
      case flags ~ name ~ opA ~ opB =>
        RawInstruction(name.getOrElse(""), opA, opB, flags)
    }

  def instruction: Parser[Instruction] =
    rawInstruction.flatMap(raw =>
      Instruction.decode(raw).fold(error => failure(error.toString()), success))

  def rawInstructions: Parser[Seq[RawInstruction]] =
    repsep(rawInstruction, newline) <~ newline.*
  def instructions: Parser[Seq[Instruction]] =
    repsep(instruction, newline) <~ newline.*
}
