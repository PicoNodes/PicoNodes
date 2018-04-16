package picoide.server

import akka.NotUsed
import akka.actor.ActorSystem
import akka.event.Logging
import akka.stream.Materializer
import akka.stream.scaladsl.Tcp.IncomingConnection
import akka.stream.scaladsl._
import akka.stream.scaladsl.Tcp.ServerBinding
import akka.util.ByteString
import java.nio.ByteOrder
import java.util.UUID
import picoide.proto.{DownloaderCommand, DownloaderEvent}
import scala.concurrent.{ExecutionContext, Future}

case class Downloader(id: UUID,
                      flow: Flow[DownloaderCommand, DownloaderEvent, NotUsed])

object DownloaderServer {
  implicit val byteOrder = ByteOrder.BIG_ENDIAN

  def emitCommand(cmd: DownloaderCommand): ByteString =
    cmd match {
      case DownloaderCommand.DownloadBytecode(bytecode) =>
        ByteString(1: Int) ++ // Type
          ByteString(bytecode.length) ++
          ByteString(bytecode: _*)
    }
  def parseEvent(msg: ByteString): Either[String, DownloaderEvent] = {
    val buf = msg.asByteBuffer
    buf.order(byteOrder)
    buf.getInt match {
      case unknownType =>
        Left(s"Unknown event of type $unknownType: $msg")
    }
  }

  def start()(implicit actorSystem: ActorSystem,
              materializer: Materializer,
              executionContext: ExecutionContext)
    : Source[Downloader, Future[ServerBinding]] = {
    val log = Logging.apply(actorSystem, getClass)

    val setupDownloader = Flow[IncomingConnection].map { conn =>
      val id = UUID.randomUUID()

      val toDownloader = Flow
        .fromSinkAndSourceCoupledMat(
          BroadcastHub.sink[DownloaderEvent],
          MergeHub.source[DownloaderCommand])((eventSource, commandSink) =>
          Downloader(id, Flow.fromSinkAndSource(commandSink, eventSource)))
        .named("toDownloader")

      val parseEvents = Flow[ByteString]
        .map(parseEvent)
        .mapConcat[DownloaderEvent] {
          case Left(err) =>
            log.warning(s"Invalid message from ${conn.remoteAddress}: $err")
            List.empty
          case Right(event) =>
            List(event)
        }
        .named("parseEvents")

      val emitCommands =
        Flow[DownloaderCommand].map(emitCommand).named("parseEvents")

      val protocol =
        Framing
          .simpleFramingProtocol(1024)
          .reversed
          .atop(BidiFlow.fromFlows(parseEvents, emitCommands))
          .joinMat(toDownloader)(Keep.right)
          .named("protocol")

      conn.handleWith(protocol)
    }

    val sslContext = DownloaderTLSConfig.sslContext

    Tcp()
      .bindTls("0.0.0.0",
               8081,
               sslContext,
               DownloaderTLSConfig.negotiateNewSession(sslContext))
      .via(setupDownloader)
      .mapMaterializedValue(_.map { binding =>
        actorSystem.log.info("Downloader server listening on {}",
                             binding.localAddress)
        binding
      })
  }
}
