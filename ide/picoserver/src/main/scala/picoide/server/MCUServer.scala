package picoide.server

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.Tcp.IncomingConnection
import akka.stream.scaladsl._
import akka.stream.scaladsl.Tcp.ServerBinding
import akka.util.ByteString
import scala.concurrent.{ExecutionContext, Future}

object NodeServer {
  def start()(implicit actorSystem: ActorSystem,
              materializer: Materializer,
              executionContext: ExecutionContext): Future[ServerBinding] = {
    val sink = Sink.foreach[IncomingConnection] { conn =>
      // val protocol = Flow[ByteString]
      //   .via(Framing.delimiter(ByteString("\n"), 256, true))
      //   .map(_.utf8String)
      //   .map(_ + "!!!\r\n")
      //   .map(ByteString(_))
      val protocol = Flow[ByteString].via(
        Framing
          .simpleFramingProtocol(1024)
          .join(
            Flow[ByteString]
          ))
      conn.handleWith(protocol)
    }

    val binding = Tcp().bind("0.0.0.0", 8081).to(sink).run()
    binding.foreach(binding =>
      actorSystem.log.info("Node server listening on {}", binding.localAddress))
    binding
  }
}
