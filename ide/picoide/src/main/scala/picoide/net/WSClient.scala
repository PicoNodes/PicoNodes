package picoide.net

import akka.NotUsed
import akka.actor.{
  Actor,
  ActorContext,
  ActorRef,
  ActorRefFactory,
  PoisonPill,
  Props
}
import akka.stream.scaladsl.BidiFlow
import scala.scalajs.js.typedarray.TypedArrayBuffer
import java.nio.ByteBuffer
import akka.stream.{BufferOverflowException, Materializer, OverflowStrategy}
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.util.ByteString
import java.io.IOException
import scala.scalajs.js
import org.scalajs.dom.raw.WebSocket
import scala.scalajs.js.typedarray.ArrayBuffer
import scala.scalajs.js.typedarray.TypedArrayBufferOps._

class WSClient(url: String, protocols: Seq[String], streamSource: ActorRef)
    extends Actor {
  private var inner: Option[WebSocket]       = None
  private var connected                      = false
  private var incomingSink: Option[ActorRef] = None

  override def preStart(): Unit = {
    val inner = new WebSocket(url, js.Array(protocols: _*))
    inner.binaryType = "arraybuffer"
    inner.onopen = _ => self ! WSClient.Connected
    inner.onclose = _ => self ! PoisonPill
    inner.onmessage = _.data match {
      case textMsg: String =>
        self ! WSClient.IncomingMessage(WSClient.TextMessage(textMsg))
      case binaryMsg: ArrayBuffer =>
        self ! WSClient.IncomingMessage(
          WSClient.BinaryMessage(TypedArrayBuffer.wrap(binaryMsg)))
    }
    inner.onerror = _ => self ! WSClient.Error
    context.watch(streamSource)
  }

  override def postStop(): Unit = {
    inner.foreach(_.close())
    connected = false
  }

  private def send(msg: WSClient.Message): Unit = msg match {
    case WSClient.TextMessage(textMsg) =>
      inner.get.send(textMsg)
    case WSClient.BinaryMessage(binMsg) =>
      inner.get.send(binMsg.arrayBuffer())
  }

  override def receive = {
    case WSClient.Connected =>
      connected = true
      incomingSink.foreach(_ ! WSClient.StreamAck)
    case WSClient.IncomingMessage(msg) =>
      streamSource ! msg
    case WSClient.Error =>
      throw new WSClient.WebSocketFailed()
    case WSClient.OutgoingMessage(msg) =>
      if (connected) {
        send(msg)
        sender ! WSClient.StreamAck
      } else {
        throw new BufferOverflowException(
          s"$sender tried to send a message before it was connected")
      }

    case WSClient.StreamInit =>
      incomingSink = Some(sender)
      if (connected) {
        sender ! WSClient.StreamAck
      }
  }
}

object WSClient {
  def connect(url: String, protocols: Seq[String] = Seq.empty)(
      implicit materializer: Materializer,
      actorFactory: ActorRefFactory): Flow[Message, Message, NotUsed] = {
    val source                      = Source.actorRef(10, OverflowStrategy.dropHead)
    val (sourceActor, sourcePreMat) = source.preMaterialize()

    val connectionActor =
      actorFactory.actorOf(Props(new WSClient(url, protocols, sourceActor)))
    // val sink = Sink.actorRefWithAck(connectionActor,
    //                                 onInitMessage = StreamInit,
    //                                 ackMessage = StreamAck,
    //                                 onCompleteMessage = PoisonPill,
    //                                 onFailureMessage = _ => PoisonPill)

    // Flow.fromSinkAndSourceCoupled(sink, sourcePreMat)
    ???
  }

  val binaryMessagesFlow
    : BidiFlow[Message, ByteBuffer, ByteBuffer, Message, NotUsed] =
    BidiFlow.fromFunctions({
      case BinaryMessage(msg) => msg
      case msg                => throw new IllegalMessageException(msg, "binary")
    }, BinaryMessage)

  class IllegalMessageException(msg: Message, expected: String)
      extends IOException(
        s"Received $msg when only $expected messages are allowed")
  class WebSocketFailed extends IOException("Websocket connection failed")

  private case object Connected
  private case object Error
  private case class IncomingMessage(msg: Message)
  private case class OutgoingMessage(msg: Message)

  private case object StreamInit
  private case object StreamAck

  sealed trait Message
  case class TextMessage(inner: String)       extends Message
  case class BinaryMessage(inner: ByteBuffer) extends Message
}
