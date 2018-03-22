package picoide

import picoide.asm.PicoAsmParser

case class SourceFile(content: Seq[String]) {
  def formatted: Seq[String] = {
    val parser = new PicoAsmParser
    content.map(
      line =>
        parser
          .parseAll(parser.rawInstruction, line)
          .map(_.toString())
          .getOrElse(line))
  }
}

case class Root(currentFile: SourceFile)
