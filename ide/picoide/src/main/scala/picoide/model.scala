package picoide

import picoide.asm.PicoAsmParser

case class SourceFile(content: String)

case class Root(currentFile: SourceFile)
