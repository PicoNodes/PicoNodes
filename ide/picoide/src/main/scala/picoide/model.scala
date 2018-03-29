package picoide

import akka.stream.scaladsl.SourceQueueWithComplete
import diode.data.Pot
import picoide.asm.PicoAsmParser
import monocle.macros._
import picoide.proto.IDECommand

case class SourceFile(content: String)

@Lenses
case class Root(currentFile: SourceFile,
                commandQueue: Pot[SourceQueueWithComplete[IDECommand]])
