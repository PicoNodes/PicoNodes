package picoide.asm

object PicoAsmFormatter {
  def formatInstruction(instr: String): Option[String] =
    PicoAsmParser
      .parseAll(PicoAsmParser.rawInstruction, instr)
      .map(_.toString())
      .map(Some(_))
      .getOrElse(None)
}
