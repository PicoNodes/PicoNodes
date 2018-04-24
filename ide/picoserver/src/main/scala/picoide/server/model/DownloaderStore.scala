package picoide.server.model

import java.security.Principal
import java.util.UUID
import javax.security.auth.x500.X500Principal
import org.cryptacular.x509.dn.{NameReader, StandardAttributeType}
import picoide.proto.DownloaderInfo
import scala.concurrent.{ExecutionContext, Future}
import PgProfile.api._
import slick.jdbc.GetResult

class DownloaderStore(implicit db: Database) {
  def find(principal: Principal)(
      implicit ec: ExecutionContext): Future[ServerDownloaderInfo] = db.run {
    val x500 = principal.asInstanceOf[X500Principal]
    val id = UUID.fromString(
      NameReader
        .readX500Principal(x500)
        .getValue(StandardAttributeType.CommonName))
    (for {
      _ <- sqlu"INSERT INTO downloaders(id) VALUES(${id.toString()}::uuid) ON CONFLICT (id) DO NOTHING"
      downloader <- ServerDownloaderInfos
        .filter(_.id === id)
        .result
        .head
    } yield downloader).transactionally
  }
}
