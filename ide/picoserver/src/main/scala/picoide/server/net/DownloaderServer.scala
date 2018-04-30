package picoide.server.net

import akka.NotUsed
import akka.actor.ActorSystem
import akka.event.Logging
import akka.stream.{Materializer, TLSProtocol, TLSRole}
import akka.stream.scaladsl.Tcp.IncomingConnection
import akka.stream.scaladsl._
import akka.stream.scaladsl.Tcp.ServerBinding
import akka.util.ByteString
import java.nio.ByteOrder
import java.util.UUID
import picoide.proto.{DownloaderCommand, DownloaderEvent, DownloaderInfo}
import picoide.server.model.{Downloader, DownloaderStore}
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._

object DownloaderServer {
  implicit val byteOrder = ByteOrder.BIG_ENDIAN

  def emitCommand(cmd: DownloaderCommand): ByteString =
    cmd match {
      case DownloaderCommand.DownloadBytecode(bytecode) =>
        ByteString.newBuilder
          .putInt(1) // Type
          .putBytes(bytecode.toArray)
          .result()
      case DownloaderCommand.Ping =>
        ByteString()
    }
  def parseEvent(msg: ByteString): Either[String, Option[DownloaderEvent]] =
    if (msg.isEmpty) {
      Right(None)
    } else {
      val buf = msg.iterator
      buf.getInt match {
        case 1 =>
          Right(Some(DownloaderEvent.DownloadedBytecode(crc = buf.getInt)))
        case unknownType =>
          Left(s"Unknown event of type $unknownType: $msg")
      }
    }

  def start(downloaderStore: DownloaderStore)(
      implicit actorSystem: ActorSystem,
      materializer: Materializer,
      executionContext: ExecutionContext)
    : Source[Downloader, Future[ServerBinding]] = {
    val log = Logging.apply(actorSystem, getClass)

    val setupDownloader = Flow[IncomingConnection]
      .mapAsyncUnordered(10) { conn =>
        val toDownloader = Flow
          .fromSinkAndSourceCoupledMat(
            BroadcastHub.sink[DownloaderEvent],
            MergeHub.source[DownloaderCommand])((eventSource, commandSink) =>
            Flow.fromSinkAndSourceCoupled(commandSink, eventSource))
          .named("toDownloader")

        val parseEvents = Flow[ByteString]
          .map(parseEvent)
          .mapConcat[DownloaderEvent] {
            case Left(err) =>
              log.warning(s"Invalid message from ${conn.remoteAddress}: $err")
              List.empty
            case Right(event) =>
              event.toList
          }
          .named("parseEvents")

        val emitCommands =
          Flow[DownloaderCommand]
            .keepAlive(10.seconds, () => DownloaderCommand.Ping)
            .map(emitCommand)
            .named("parseEvents")

        val protocol =
          Framing
            .simpleFramingProtocol(1024)
            .reversed
            .atop(BidiFlow.fromFlows(parseEvents, emitCommands))
            .joinMat(toDownloader)(Keep.right)
            .named("protocol")

        val authenticator =
          TLSClientAuthStage.bidiBs(downloaderStore.find(_).map(Some(_)))

        val sslContext = DownloaderTLSConfig.sslContext
        val encrypted =
          TLS(sslContext,
              DownloaderTLSConfig.negotiateNewSession(sslContext),
              TLSRole.server).reversed
            .atopMat(authenticator)(Keep.right)
            .joinMat(protocol)((info, flow) =>
              info.map(_.map(Downloader(_, flow))))

        conn.handleWith(encrypted)
      }

    Tcp()
      .bind("0.0.0.0", 8081)
      .via(setupDownloader)
      .log("new-downloader")
      .mapConcat(_.toList)
      .mapMaterializedValue(_.map { binding =>
        actorSystem.log.info("Downloader server listening on {}",
                             binding.localAddress)
        binding
      })
  }
}
