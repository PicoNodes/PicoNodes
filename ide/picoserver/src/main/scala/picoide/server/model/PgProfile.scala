package picoide.server.model

import com.github.tminglei.slickpg.ExPostgresProfile
import java.util.UUID
import slick.jdbc.{JdbcCapabilities, SetParameter}

class PgProfile extends ExPostgresProfile {
  override protected def computeCapabilities =
    super.computeCapabilities + JdbcCapabilities.insertOrUpdate
}

object PgProfile extends PgProfile
