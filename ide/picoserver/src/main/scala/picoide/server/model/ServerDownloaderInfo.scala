package picoide.server.model

import java.util.UUID
import picoide.proto.DownloaderInfo
import picoide.server.model.PgProfile.api._

/**
  * Extended variant of [[picoide.proto.DownloaderInfo]], which also contains
  * information that only the server cares about
  */
case class ServerDownloaderInfo(id: UUID, label: Option[String]) {
  def toProto = DownloaderInfo(id = id, label = label)
}

class ServerDownloaderInfos(tag: Tag)
    extends Table[ServerDownloaderInfo](tag, "downloaders") {
  def id    = column[UUID]("id")
  def label = column[Option[String]]("label")

  override def * =
    (id, label) <> (ServerDownloaderInfo.tupled, ServerDownloaderInfo.unapply)
}

object ServerDownloaderInfos extends TableQuery(new ServerDownloaderInfos(_))
