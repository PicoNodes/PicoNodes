package picoide.server.model

import java.time.Instant
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
import PgProfile.api._

class SourceFileManager(implicit db: Database) {
  def list()(implicit ec: ExecutionContext): Future[Seq[SourceFileRef]] =
    db.run(
      SourceFileRevisions
        .sorted(_.createdAt.desc)
        .distinctOn(_.fileId)
        .filter(_.content.isDefined)
        .flatMap(_.file)
        .result
    )

  def get(id: UUID)(implicit ec: ExecutionContext): Future[Option[SourceFile]] =
    db.run(
      (for {
        rev  <- SourceFileRevisions.sorted(_.createdAt.desc)
        file <- rev.file
        if file.id === id
      } yield (file, rev)).result.headOption.map(
        _.map(SourceFile.tupled).filter(_.currentRevision.content.isDefined))
    )

  def save(ref: SourceFileRef, content: String)(
      implicit ec: ExecutionContext): Future[Unit] =
    db.run(
      (for {
        _ <- SourceFileRefs.insertOrUpdate(ref)
        _ <- SourceFileRevisions += SourceFileRevision(
          id = UUID.randomUUID(),
          file = ref.id,
          content = Some(content),
          createdAt = Instant.now())
      } yield ()).transactionally
    )
}
