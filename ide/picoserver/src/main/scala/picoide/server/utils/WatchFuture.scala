package picoide.server.utils

import akka.stream.{FanInShape, FanInShape2}
import akka.stream.stage.{GraphStageLogic, InHandler, OutHandler}
import akka.stream.{Attributes, FlowShape, Inlet, Outlet}
import akka.stream.stage.GraphStage
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class WatchFuture[T, F] extends GraphStage[FanInShape2[T, Future[F], T]] {
  val in             = Inlet[T]("WatchFuture.in")
  val future         = Inlet[Future[F]]("WatchFuture.future")
  val out            = Outlet[T]("WatchFuture.out")
  override def shape = new FanInShape2(in, future, out)

  override def createLogic(inheritedAttributes: Attributes) =
    new GraphStageLogic(shape) {
      implicit def executionContext = materializer.executionContext

      override def preStart(): Unit = {
        pull(future)
        pull(in)
      }

      setHandler(
        future,
        new InHandler() {
          override def onPush(): Unit = {
            grab(future).onComplete {
              case Success(_) =>
                getAsyncCallback[Unit] { _ =>
                  completeStage()
                }.invoke(())
              case Failure(ex) =>
                getAsyncCallback[Unit] { _ =>
                  failStage(ex)
                }.invoke(())
            }
            cancel(future)
          }
        }
      )

      setHandlers(
        in,
        out,
        new InHandler with OutHandler {
          override def onPush(): Unit =
            if (isAvailable(out)) {
              push(out, grab(in))
              pull(in)
            }

          override def onPull(): Unit =
            if (isAvailable(in)) {
              push(out, grab(in))
              pull(in)
            }
        }
      )
    }
}

object WatchFuture {
  def apply[T, F]: WatchFuture[T, F] = new WatchFuture()
}
