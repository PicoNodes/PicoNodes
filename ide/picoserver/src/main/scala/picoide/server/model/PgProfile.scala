package picoide.server.model

import com.github.tminglei.slickpg.{ExPostgresProfile, PgDate2Support}
import java.util.UUID
import slick.jdbc.{GetResult, JdbcCapabilities, SetParameter}

trait PgProfile extends ExPostgresProfile with PgDate2Support {
  override protected def computeCapabilities =
    super.computeCapabilities + JdbcCapabilities.insertOrUpdate

  trait API extends super.API with DateTimeImplicits {
    implicit def getResultUuid =
      GetResult(r => uuidColumnType.fromBytes(r.nextBytes()))
  }
  override val api = new API {}
}

object PgProfile extends PgProfile
