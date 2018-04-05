package picoide

import akka.stream.scaladsl.SourceQueueWithComplete
import diode.data.Pot
import java.util.UUID
import picoide.asm.PicoAsmParser
import monocle.macros._
import picoide.proto.{DownloaderInfo, IDECommand}

@Lenses
case class SourceFile(content: String)

@Lenses
case class Downloaders(all: Map[UUID, DownloaderInfo],
                       current: Option[DownloaderInfo] = None)

@Lenses
case class Root(currentFile: SourceFile,
                commandQueue: Pot[SourceQueueWithComplete[IDECommand]],
                downloaders: Pot[Downloaders])
