package picoide.net

import akka.NotUsed
import akka.stream.scaladsl.Flow
import picoide.proto.{IDECommand, IDEEvent}

object IDEClient {
  def connect(url: String): Flow[IDECommand, IDEEvent, NotUsed] = ???
}
