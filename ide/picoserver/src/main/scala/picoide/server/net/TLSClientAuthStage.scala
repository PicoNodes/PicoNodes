package picoide.server.net

import akka.stream.scaladsl.{BidiFlow, Flow, Keep}
import akka.stream.stage.{InHandler, OutHandler}
import akka.stream.{Attributes, FlowShape}
import akka.stream.stage.{GraphStageLogic, GraphStageWithMaterializedValue}
import akka.stream.{BidiShape, Inlet, Outlet, TLSProtocol}
import akka.stream.stage.GraphStage
import akka.util.{ByteString, Timeout}
import java.io.IOException
import java.security.Principal
import java.util.UUID
import picoide.proto.DownloaderInfo
import scala.concurrent.{Future, Promise}
import scala.concurrent.duration._
import scala.util.{Failure, Success}

class TLSClientAuthStage(
    authenticator: Principal => Future[Option[DownloaderInfo]])
    extends GraphStageWithMaterializedValue[
      FlowShape[TLSProtocol.SslTlsInbound, TLSProtocol.SslTlsInbound],
      Future[Option[DownloaderInfo]]] {
  val in  = Inlet[TLSProtocol.SslTlsInbound]("in")
  val out = Outlet[TLSProtocol.SslTlsInbound]("out")

  val shape = FlowShape(in, out)

  def createLogicAndMaterializedValue(inheritedAttributes: Attributes) = {
    val idPromise = Promise[Option[DownloaderInfo]]()
    val idFuture  = idPromise.future

    val logic = new GraphStageLogic(shape) {
      override def preStart(): Unit = {
        materializer.scheduleOnce(2.seconds, () => idPromise.trySuccess(None))

        val failAsync = getAsyncCallback(failStage)
        idFuture.onComplete {
          case Failure(ex) =>
            failAsync.invoke(ex)
          case Success(None) =>
            failAsync.invoke(
              new IllegalAccessError("Client failed to authenticate"))
          case Success(Some(_)) =>
        }(materializer.executionContext)
      }

      override def postStop(): Unit =
        idPromise.trySuccess(None)

      setHandler(
        in,
        new InHandler {
          override def onPush(): Unit =
            grab(in) match {
              case bytes: TLSProtocol.SessionBytes =>
                idPromise.tryCompleteWith(
                  authenticator(bytes.peerPrincipal.get))
                idFuture.foreach(getAsyncCallback[Option[DownloaderInfo]] {
                  case Some(_) =>
                    push(out, bytes)
                    passAlong(in, out)
                  case None =>
                }.invoke)(materializer.executionContext)
              case trunc: TLSProtocol.SessionTruncated =>
                push(out, trunc)
            }
        }
      )

      setHandler(out, new OutHandler {
        override def onPull(): Unit =
          if (!hasBeenPulled(in)) {
            pull(in)
          }

      })
    }
    (logic, idFuture)
  }
}

object TLSClientAuthStage {
  def apply(authenticator: Principal => Future[Option[DownloaderInfo]])
    : Flow[TLSProtocol.SslTlsInbound,
           TLSProtocol.SslTlsInbound,
           Future[Option[DownloaderInfo]]] =
    Flow.fromGraph(new TLSClientAuthStage(authenticator))

  def bidiBs(authenticator: Principal => Future[Option[DownloaderInfo]])
    : BidiFlow[TLSProtocol.SslTlsInbound,
               ByteString,
               ByteString,
               TLSProtocol.SslTlsOutbound,
               Future[Option[DownloaderInfo]]] =
    BidiFlow.fromFlowsMat(
      this(authenticator).map {
        case TLSProtocol.SessionBytes(_, bytes) => bytes
        case TLSProtocol.SessionTruncated =>
          throw new IOException("Session truncated!")
      },
      Flow[ByteString].map(TLSProtocol.SendBytes(_))
    )(Keep.left)
}
