package picoide

import picoide.asm.PicoAsmParser

case class SourceFile(content: String) {
  def formatted: String = {
    val parser = new PicoAsmParser
    parser
      .parseAll(parser.rawInstructions, content)
      .map(_.mkString("\r\n"))
      .getOrElse(content)
  }
}

case class Root(currentFile: SourceFile)
