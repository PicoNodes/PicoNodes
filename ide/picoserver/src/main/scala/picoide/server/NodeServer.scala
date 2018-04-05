package picoide.server

import akka.NotUsed
import akka.actor.ActorSystem
import akka.event.Logging
import akka.stream.Materializer
import akka.stream.scaladsl.Tcp.IncomingConnection
import akka.stream.scaladsl._
import akka.stream.scaladsl.Tcp.ServerBinding
import akka.util.ByteString
import java.util.UUID
import picoide.proto.{ProgrammerCommand, ProgrammerEvent}
import scala.concurrent.{ExecutionContext, Future}

case class ProgrammerNode(
    id: UUID,
    flow: Flow[ProgrammerCommand, ProgrammerEvent, NotUsed])

object NodeServer {
  def emitCommand(cmd: ProgrammerCommand): ByteString = ???
  def parseEvent(msg: ByteString): Either[String, ProgrammerEvent] = {
    val buf = msg.asByteBuffer
    buf.getInt match {
      case unknownType =>
        Left(s"Unknown event of type $unknownType: $msg")
    }
  }

  def start()(implicit actorSystem: ActorSystem,
              materializer: Materializer,
              executionContext: ExecutionContext)
    : Source[ProgrammerNode, Future[ServerBinding]] = {
    val log = Logging.apply(actorSystem, getClass)

    val setupNode = Flow[IncomingConnection].map { conn =>
      val id = UUID.randomUUID()

      val toNode = Flow
        .fromSinkAndSourceMat(
          BroadcastHub.sink[ProgrammerEvent],
          MergeHub.source[ProgrammerCommand])((eventSource, commandSink) =>
          ProgrammerNode(id, Flow.fromSinkAndSource(commandSink, eventSource)))
        .named("toNode")

      val parseEvents = Flow[ByteString]
        .map(parseEvent)
        .mapConcat[ProgrammerEvent] {
          case Left(err) =>
            log.warning(s"Invalid message from ${conn.remoteAddress}: $err")
            List.empty
          case Right(event) =>
            List(event)
        }
        .named("parseEvents")

      val emitCommands =
        Flow[ProgrammerCommand].map(emitCommand).named("parseEvents")

      val protocol =
        Framing
          .simpleFramingProtocol(1024)
          .reversed
          .atop(BidiFlow.fromFlows(parseEvents, emitCommands))
          .joinMat(toNode)(Keep.right)
          .named("protocol")

      conn.handleWith(protocol)
    }

    Tcp()
      .bind("0.0.0.0", 8081)
      .via(setupNode)
      .mapMaterializedValue(_.map { binding =>
        actorSystem.log.info("Node server listening on {}",
                             binding.localAddress)
        binding
      })
  }
}
