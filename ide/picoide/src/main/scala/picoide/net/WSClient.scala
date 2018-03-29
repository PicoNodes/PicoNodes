package picoide.net

import akka.stream.stage.{InHandler, OutHandler}
import akka.{Done, NotUsed}
import akka.actor.{
  Actor,
  ActorContext,
  ActorRef,
  ActorRefFactory,
  PoisonPill,
  Props
}
import org.scalajs.dom.raw.MessageEvent
import scala.concurrent.Promise
import scala.concurrent.Future
import akka.stream.stage.GraphStageWithMaterializedValue
import akka.stream.stage.GraphStageLogic
import akka.stream.Attributes
import akka.stream.Outlet
import akka.stream.Inlet
import akka.stream.FlowShape
import akka.stream.stage.GraphStage
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

import picoide.net.WSClient.Message

class WSClient(url: String, protocols: Seq[String])
    extends GraphStageWithMaterializedValue[FlowShape[Message, Message],
                                            Future[Done]] {
  val in             = Inlet[Message]("WSClient.in")
  val out            = Outlet[Message]("WSClient.out")
  override val shape = FlowShape(in, out)

  override def createLogicAndMaterializedValue(
      inheritedAttributes: Attributes): (GraphStageLogic, Future[Done]) = {
    val connectedPromise = Promise[Done]()
    val logic = new GraphStageLogic(shape) {
      private var inner: Option[WebSocket] = None

      override def preStart(): Unit = {
        val onOpenCb = getAsyncCallback[Unit] { _ =>
          connectedPromise.success(Done)
          pull(in)
        }
        val onCloseCb = getAsyncCallback[Unit] { _ =>
          completeStage()
        }
        val onMessageCb = getAsyncCallback[MessageEvent](_.data match {
          case textMsg: String =>
            push(out, WSClient.TextMessage(textMsg))
          case binaryMsg: ArrayBuffer =>
            push(out, WSClient.BinaryMessage(TypedArrayBuffer.wrap(binaryMsg)))
        })
        val onErrorCb = getAsyncCallback[Unit] { _ =>
          val reason = new WSClient.WebSocketFailed()
          failStage(reason)
          if (!connectedPromise.isCompleted) {
            connectedPromise.failure(reason)
          }
        }

        val inner = new WebSocket(url, js.Array(protocols: _*))
        inner.binaryType = "arraybuffer"
        inner.onopen = _ => onOpenCb.invoke(())
        inner.onclose = _ => onCloseCb.invoke(())
        inner.onmessage = onMessageCb.invoke(_)
        inner.onerror = _ => onErrorCb.invoke(())
      }

      override def postStop(): Unit =
        inner.foreach(_.close())

      setHandlers(
        in,
        out,
        new InHandler with OutHandler {
          override def onPush(): Unit = {
            grab(in) match {
              case WSClient.TextMessage(textMsg) =>
                inner.get.send(textMsg)
              case WSClient.BinaryMessage(binMsg) =>
                inner.get.send(binMsg.arrayBuffer())
            }
            pull(in)
          }

          override def onPull(): Unit = {
            // Do nothing, since we can't backpressure websockets :(
          }
        }
      )
    }
    (logic, connectedPromise.future)
  }
}

object WSClient {
  def connect(url: String, protocols: Seq[String] = Seq.empty)
    : Flow[Message, Message, Future[Done]] =
    Flow.fromGraph(new WSClient(url, protocols))

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
