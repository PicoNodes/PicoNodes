package picoide

import picoide.asm.PicoAsmParser

case class SourceFile(content: String) {
  def formatted: String = {
    val parser = new PicoAsmParser
    content.lines
      .map(
        line =>
          parser
            .parseAll(parser.rawInstruction, line)
            .map(_.toString())
            .getOrElse(line))
      .mkString("\r\n")
  }
}

case class Root(currentFile: SourceFile)
