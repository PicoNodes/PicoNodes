package picoide.server.model

import java.time.Instant
import java.util.UUID
import PgProfile.api._
import picoide.proto

case class SourceFileRef(id: UUID, name: String) {
  def toProto = proto.SourceFileRef(id = id, name = name)
}
object SourceFileRef {
  def fromProto(p: proto.SourceFileRef) = SourceFileRef(p.id, p.name)
}
case class SourceFile(ref: SourceFileRef, currentRevision: SourceFileRevision) {
  def toProto =
    proto.SourceFile(ref = ref.toProto, content = currentRevision.content.get)
}
case class SourceFileRevision(id: UUID,
                              file: UUID,
                              content: Option[String],
                              createdAt: Instant)

class SourceFileRefs(tag: Tag)
    extends Table[SourceFileRef](tag, "source_files") {
  def id   = column[UUID]("id", O.PrimaryKey)
  def name = column[String]("name")

  def * = (id, name) <> ((SourceFileRef.apply _).tupled, SourceFileRef.unapply)
}

class SourceFileRevisions(tag: Tag)
    extends Table[SourceFileRevision](tag, "source_file_revisions") {
  def id        = column[UUID]("id", O.PrimaryKey)
  def fileId    = column[UUID]("file")
  def content   = column[Option[String]]("content")
  def createdAt = column[Instant]("created_at")

  def file = foreignKey("file_fk", fileId, SourceFileRefs)(_.id)

  def * =
    (id, fileId, content, createdAt) <> (SourceFileRevision.tupled, SourceFileRevision.unapply)
}

/**
  * This is just a view, **do not write**
  */
class SourceFilesCurrent(tag: Tag)
    extends Table[(UUID, UUID)](tag, "source_files_current") {
  def fileId     = column[UUID]("file")
  def revisionId = column[UUID]("revision")

  def file = foreignKey("file_fk", fileId, SourceFileRefs)(_.id)
  def revision =
    foreignKey("revision_fk", revisionId, SourceFileRevisions)(_.id)

  def * = (fileId, revisionId)
}

object SourceFileRefs extends TableQuery(new SourceFileRefs(_))

object SourceFileRevisions extends TableQuery(new SourceFileRevisions(_))

object SourceFilesCurrent extends TableQuery(new SourceFilesCurrent(_))
