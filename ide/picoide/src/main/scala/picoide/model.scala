package picoide

import akka.stream.scaladsl.SourceQueueWithComplete
import diode.Action
import diode.data.Pot
import java.util.UUID
import monocle.Lens
import picoide.asm.PicoAsmParser
import monocle.macros._
import picoide.proto.{
  DownloaderEvent,
  DownloaderInfo,
  IDECommand,
  SourceFile,
  SourceFileRef
}

case class Dirtying[T](value: T,
                       isDirty: Boolean,
                       nextCleanAction: Option[Action] = None) {
  def isClean: Boolean = !isDirty
}

object Dirtying {
  def lensAdapter[A, B](lens: Lens[A, B]): Lens[Dirtying[A], B] =
    value ^|-> lens

  def value[T] =
    Lens[Dirtying[T], T](_.value)(newVal =>
      old =>
        old.copy(value = newVal, isDirty = old.isDirty || newVal != old.value))
  def isDirty[T]         = GenLens[Dirtying[T]](_.isDirty)
  def nextCleanAction[T] = GenLens[Dirtying[T]](_.nextCleanAction)
}

@Lenses
case class Downloaders(all: Map[UUID, DownloaderInfo],
                       current: Option[DownloaderInfo] = None,
                       events: Seq[DownloaderEvent] = Seq())

@Lenses
case class Root(currentFile: Pot[Dirtying[SourceFile]],
                knownFiles: Pot[Seq[SourceFileRef]],
                commandQueue: Pot[SourceQueueWithComplete[IDECommand]],
                downloaders: Pot[Downloaders])
