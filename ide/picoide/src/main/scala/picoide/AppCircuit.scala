package picoide

import akka.stream.Materializer
import akka.stream.scaladsl.SourceQueueWithComplete
import diode.Action
import diode.data.{Failed, Ready}

import diode.{ActionHandler, ActionResult, Circuit, Effect, NoAction}
import diode.data.{Pot, PotAction, PotState}
import diode.react.ReactConnector

import picoide.net.IDEClient
import picoide.proto.{DownloaderInfo, IDECommand, IDEEvent}

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class AppCircuit(implicit materializer: Materializer)
    extends Circuit[Root]
    with ReactConnector[Root] {
  override def initialModel = Root(
    currentFile = SourceFile("""  mov 1 up
                               |  mov 2 null
                               |  mov 3 acc
                               |
                               |+ mov 4 acc
                               |- mov 6 acc
                               |  mov 5 null
""".stripMargin),
    commandQueue = Pot.empty,
    downloaders = Pot.empty
  )

  override def actionHandler =
    composeHandlers(editorHandler,
                    commandQueueHandler,
                    downloadersHandler,
                    ideEventHandler)

  def editorHandler =
    new ActionHandler[Root, SourceFile](zoomTo(_.currentFile)) {
      override def handle = {
        case Actions.CurrentFile.Modify(newContent) =>
          updated(SourceFile.content.set(newContent)(value))
      }
    }

  def commandQueueHandler =
    new ActionHandler[Root, Pot[SourceQueueWithComplete[IDECommand]]](
      zoomTo(_.commandQueue)) {
      override def handle = {
        case action: Actions.CommandQueue.Update =>
          import PotState._
          action.handle {
            case PotEmpty =>
              updated(value.pending(),
                      IDEClient.connectToCircuit("ws://localhost:8080/connect",
                                                 AppCircuit.this))
            case PotPending =>
              noChange
            case PotReady =>
              updated(action.potResult,
                      Effect.action(Actions.Downloaders.Update(Pot.empty)))
            case PotUnavailable =>
              updated(value.unavailable())
            case PotFailed =>
              updated(value.fail(action.result.failed.get))
          }
      }
    }

  def downloadersHandler =
    new ActionHandler[Root, Pot[Downloaders]](zoomTo(_.downloaders)) {
      override def handle: PartialFunction[Any, ActionResult[Root]] = {
        case action: Actions.Downloaders.Update =>
          import PotState._
          action.handle {
            case PotEmpty =>
              updated(value.pending(),
                      IDEClient.requestDownloaderList(zoomTo(_.commandQueue)))
            case PotPending =>
              noChange
            case PotReady =>
              updated(
                action.potResult
                  .map(_.map(dler => dler.id -> dler).toMap)
                  .map(Downloaders(_)))
            case PotUnavailable =>
              updated(value.unavailable())
            case PotFailed =>
              updated(
                value.fail(action.result.failed.get),
                Effect.action(
                  Actions.Downloaders.Update(Failed(action.result.failed.get))))
          }
        case Actions.Downloaders.Add(downloader) =>
          updated(
            value.map(
              Downloaders.all.modify(_ + (downloader.id -> downloader))))
        case Actions.Downloaders.Remove(downloader) =>
          updated(value.map(Downloaders.all.modify(_ - downloader.id)))
        case Actions.Downloaders.Select(downloader) =>
          effectOnly(
            IDEClient.selectDownloader(downloader, zoomTo(_.commandQueue)))
        case Actions.Downloaders.Selected(downloader) =>
          updated(value.map(Downloaders.current.set(downloader)))
      }
    }

  def ideEventHandler =
    new ActionHandler(zoomRW(identity(_))((_, x) => x)) {
      def toAction(event: IDEEvent): Action = event match {
        case IDEEvent.AvailableDownloaders(downloaders) =>
          Actions.Downloaders.Update(Ready(downloaders.toSet))
        case IDEEvent.AvailableDownloaderAdded(downloader) =>
          Actions.Downloaders.Add(downloader)
        case IDEEvent.AvailableDownloaderRemoved(downloader) =>
          Actions.Downloaders.Remove(downloader)
        case IDEEvent.Pong =>
          NoAction
        case IDEEvent.DownloaderSelected(downloader) =>
          Actions.Downloaders.Selected(
            for {
              id    <- downloader
              state <- value.downloaders.toOption
              dler  <- state.all.get(id)
            } yield dler
          )
      }

      override def handle = {
        case Actions.IDEEvent.Received(event) =>
          effectOnly(Effect.action(toAction(event)))
      }
    }
}
