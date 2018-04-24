package picoide.server.model

import java.util.UUID
import picoide.proto.DownloaderInfo
import picoide.server.model.PgProfile.api._

/**
  * Extended variant of [[picoide.proto.DownloaderInfo]], which also contains
  * information that only the server cares about
  */
case class ServerDownloaderInfo(id: UUID) {
  def toProto = DownloaderInfo(id = id)
}

class ServerDownloaderInfos(tag: Tag)
    extends Table[ServerDownloaderInfo](tag, "downloaders") {
  def id = column[UUID]("id")

  override def * =
    (id) <> (ServerDownloaderInfo.apply, ServerDownloaderInfo.unapply)
}

object ServerDownloaderInfos extends TableQuery(new ServerDownloaderInfos(_))
