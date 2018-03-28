package picoide.net

import akka.NotUsed
import akka.actor.ActorRefFactory
import akka.stream.Materializer
import akka.stream.scaladsl._
import java.nio.ByteBuffer
import boopickle.Default._
import picoide.proto.{IDECommand, IDEEvent}
import picoide.proto.IDEPicklers._

object IDEClient {
  val protocolPickler
    : BidiFlow[ByteBuffer, IDEEvent, IDECommand, ByteBuffer, NotUsed] =
    BidiFlow
      .fromFunctions(Unpickle[IDEEvent].fromBytes(_),
                     Pickle.intoBytes[IDECommand](_))
      .named("protocolPickler")

  def connect(url: String)(
      implicit materializer: Materializer,
      actorFactory: ActorRefFactory): Flow[IDECommand, IDEEvent, NotUsed] =
    WSClient
      .connect(url, Seq("picoide"))
      .join(WSClient.binaryMessagesFlow)
      .join(protocolPickler)
}
