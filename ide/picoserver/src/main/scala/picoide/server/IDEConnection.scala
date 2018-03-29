package picoide.server

import akka.NotUsed
import akka.actor.ActorRef
import akka.http.scaladsl.model.ws.{BinaryMessage, Message}
import akka.stream.scaladsl.{Flow, Sink, Source}

object IDEConnection {
  def webSocketHandler(
      nodeRegistry: ActorRef): Flow[Message, Message, NotUsed] =
    Flow.fromSinkAndSource(Sink.foreach(println(_)), Source.maybe)
}
