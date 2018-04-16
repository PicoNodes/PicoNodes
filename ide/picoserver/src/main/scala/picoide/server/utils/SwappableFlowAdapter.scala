package picoide.server.utils

import akka.NotUsed
import akka.stream.{OverflowStrategy, QueueOfferResult}
import akka.stream.scaladsl.{
  Keep,
  Sink,
  SinkQueueWithCancel,
  Source,
  SourceQueueWithComplete
}
import scala.util.Failure
import scala.util.Success
import scala.util.Try
import akka.stream.stage.{GraphStageLogic, InHandler, OutHandler}
import akka.stream.{Attributes, FanInShape2, Inlet, Outlet}
import akka.stream.scaladsl.Flow
import akka.stream.stage.GraphStage

class SwappableFlowAdapter[In, Out]
    extends GraphStage[FanInShape2[In, Flow[In, Out, NotUsed], Out]] {
  val in             = Inlet[In]("SwappableFlowAdapter.in")
  val flow           = Inlet[Flow[In, Out, NotUsed]]("SwappableFlowAdapter.flow")
  val out            = Outlet[Out]("SwappableFlowAdapter.out")
  override def shape = new FanInShape2(in, flow, out)

  override def createLogic(inheritedAttributes: Attributes) =
    new GraphStageLogic(shape) {
      implicit def executionContext = materializer.executionContext

      var queues
        : Option[(SourceQueueWithComplete[In], SinkQueueWithCancel[Out])] = None

      var pushOnConnect = false

      override def preStart(): Unit = {
        pull(in)
        pull(flow)
      }

      override def postStop(): Unit =
        queues.foreach {
          case (source, sink) =>
            source.complete()
            sink.cancel()
        }

      def tryToPush(): Unit = queues match {
        case None =>
          pushOnConnect = true
        case oldQueues @ Some((sourceQueue, sinkQueue)) =>
          sinkQueue
            .pull()
            .onComplete(getAsyncCallback[Try[Option[Out]]] {
              case Success(Some(x)) => push(out, x)
              case Success(None) =>
                sourceQueue.complete()
                sinkQueue.cancel()
                if (oldQueues == queues) {
                  queues = None
                  pushOnConnect = true
                } else {
                  tryToPush()
                }
              case Failure(ex) =>
                failStage(ex)
            }.invoke)
      }

      setHandlers(
        in,
        out,
        new InHandler with OutHandler {
          override def onPush(): Unit = queues match {
            case None =>
              grab(in)
              pull(in)
            case Some((sourceQueue, sinkQueue)) =>
              sourceQueue
                .offer(grab(in))
                .foreach(getAsyncCallback[QueueOfferResult] {
                  case QueueOfferResult.Enqueued | QueueOfferResult.Dropped =>
                    pull(in)
                  case QueueOfferResult.QueueClosed =>
                    sinkQueue.cancel()
                    pull(in)
                  case QueueOfferResult.Failure(ex) =>
                    failStage(ex)
                }.invoke)
          }
          override def onPull(): Unit = tryToPush()
        }
      )

      setHandler(
        flow,
        new InHandler {
          override def onPush(): Unit = {
            queues.foreach {
              case (sourceQueue, sinkQueue) =>
                sourceQueue.complete()
                sinkQueue.cancel()
            }
            val (sourceQueue, sinkQueue) =
              Source
                .queue(1, OverflowStrategy.backpressure)
                .via(grab(flow))
                .toMat(Sink.queue())(Keep.both)
                .run()(materializer)
            queues = Some((sourceQueue, sinkQueue))
            if (pushOnConnect) {
              pushOnConnect = false
              tryToPush()
            }
            pull(flow)
          }
        }
      )
    }
}
