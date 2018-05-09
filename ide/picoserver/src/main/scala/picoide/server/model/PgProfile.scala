package picoide.server.model

import com.github.tminglei.slickpg.{ExPostgresProfile, PgDate2Support}
import java.util.UUID
import slick.jdbc.{JdbcCapabilities, SetParameter}

trait PgProfile extends ExPostgresProfile with PgDate2Support {
  override protected def computeCapabilities =
    super.computeCapabilities + JdbcCapabilities.insertOrUpdate

  trait API extends super.API with DateTimeImplicits
  override val api = new API {}
}

object PgProfile extends PgProfile
