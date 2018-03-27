package picoide.server

import akka.NotUsed
import akka.actor.ActorRef
import akka.http.scaladsl.model.ws.{BinaryMessage, Message}
import akka.stream.scaladsl.Flow

object IDEConnection {
  def webSocketHandler(
      nodeRegistry: ActorRef): Flow[Message, Message, NotUsed] =
    ???
}
