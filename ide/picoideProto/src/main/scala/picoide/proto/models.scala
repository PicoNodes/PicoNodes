package picoide.proto

import java.util.UUID
import monocle.macros.Lenses

@Lenses
case class SourceFileRef(id: UUID, name: String)

@Lenses
case class SourceFile(ref: SourceFileRef, content: String)
