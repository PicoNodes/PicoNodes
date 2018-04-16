package picoide.server.net

import akka.stream.scaladsl.{BidiFlow, Flow, Keep}
import akka.stream.stage.{InHandler, OutHandler}
import akka.stream.{Attributes, FlowShape}
import akka.stream.stage.{GraphStageLogic, GraphStageWithMaterializedValue}
import akka.stream.{BidiShape, Inlet, Outlet, TLSProtocol}
import akka.stream.stage.GraphStage
import akka.util.ByteString
import java.io.IOException
import java.security.Principal
import java.util.UUID
import picoide.proto.DownloaderInfo
import scala.concurrent.{Future, Promise}

class TLSClientAuthStage(authenticator: Principal => Future[DownloaderInfo])
    extends GraphStageWithMaterializedValue[
      FlowShape[TLSProtocol.SslTlsInbound, TLSProtocol.SslTlsInbound],
      Future[DownloaderInfo]] {
  val in  = Inlet[TLSProtocol.SslTlsInbound]("in")
  val out = Outlet[TLSProtocol.SslTlsInbound]("out")

  val shape = FlowShape(in, out)

  def createLogicAndMaterializedValue(inheritedAttributes: Attributes) = {
    val idPromise = Promise[DownloaderInfo]()
    val logic = new GraphStageLogic(shape) {
      setHandler(
        in,
        new InHandler {
          override def onPush(): Unit =
            grab(in) match {
              case bytes: TLSProtocol.SessionBytes =>
                println(bytes.peerCertificates)
                idPromise.completeWith(authenticator(bytes.peerPrincipal.get))
                idPromise.future.foreach { _ =>
                  push(out, bytes)
                  passAlong(in, out)
                }(materializer.executionContext)
              case trunc: TLSProtocol.SessionTruncated =>
                push(out, trunc)
            }
        }
      )

      setHandler(out, new OutHandler {
        override def onPull(): Unit =
          pull(in)

      })
    }
    (logic, idPromise.future)
  }
}

object TLSClientAuthStage {
  def apply(authenticator: Principal => Future[DownloaderInfo])
    : Flow[TLSProtocol.SslTlsInbound,
           TLSProtocol.SslTlsInbound,
           Future[DownloaderInfo]] =
    Flow.fromGraph(new TLSClientAuthStage(authenticator))

  def bidiBs(authenticator: Principal => Future[DownloaderInfo])
    : BidiFlow[TLSProtocol.SslTlsInbound,
               ByteString,
               ByteString,
               TLSProtocol.SslTlsOutbound,
               Future[DownloaderInfo]] =
    BidiFlow.fromFlowsMat(
      this(authenticator).map {
        case TLSProtocol.SessionBytes(_, bytes) => bytes
        case TLSProtocol.SessionTruncated =>
          throw new IOException("Session truncated!")
      },
      Flow[ByteString].map(TLSProtocol.SendBytes(_))
    )(Keep.left)
}
