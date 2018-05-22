package picoide

import akka.stream.Materializer
import akka.stream.scaladsl.SourceQueueWithComplete
import diode.Action
import diode.data.{Failed, Ready}

import diode.{ActionHandler, ActionResult, Circuit, Effect, NoAction}
import diode.data.{Pot, PotAction, PotState}
import diode.react.ReactConnector
import java.util.UUID

import picoide.net.IDEClient
import picoide.proto.{
  DownloaderEvent,
  DownloaderInfo,
  IDECommand,
  IDEEvent,
  SourceFile,
  SourceFileRef
}
import picoide.utils._

import org.scalajs.dom

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class AppCircuit(implicit materializer: Materializer)
    extends Circuit[Root]
    with ReactConnector[Root] {
  override def initialModel = Root(
    currentFile = Pot.empty.ready(
      Dirtying(
        SourceFile(
          SourceFileRef(
            UUID.randomUUID(),
            "Example"
          ),
          """  mov 1 up
            |  mov 2 null
            |  mov 3 acc
            |
            |+ mov 4 acc
            |- mov 6 acc
            |  mov 5 null
""".stripMargin
        ),
        isDirty = false
      )
    ),
    knownFiles = Pot.empty,
    commandQueue = Pot.empty,
    downloaders = Pot.empty
  )

  override def actionHandler =
    composeHandlers(editorHandler,
                    commandQueueHandler,
                    filesHandler,
                    downloadersHandler,
                    ideEventHandler)

  private def commandQueue = zoom(_.commandQueue).apply()

  def editorHandler =
    new ActionHandler[Root, Pot[Dirtying[SourceFile]]](zoomTo(_.currentFile)) {
      override def handle = {
        case Actions.CurrentFile.Modify(newContent) =>
          updated(
            value.map((Dirtying.value ^|-> SourceFile.content).set(newContent)))
        case Actions.CurrentFile.Rename(newName) =>
          updated(
            value.map(
              (Dirtying.value ^|-> SourceFile.ref ^|-> SourceFileRef.name)
                .set(newName)))
        case Actions.CurrentFile.CreateNew =>
          updated(
            value.ready(
              Dirtying(SourceFile(SourceFileRef(UUID.randomUUID(), "Unnamed"),
                                  ""),
                       isDirty = false)))
        case Actions.CurrentFile.Save =>
          effectOnly(IDEClient.saveFile(value.get.value, commandQueue))
        case Actions.CurrentFile.Load(file) =>
          effectOnly(IDEClient.loadFile(file, commandQueue))
        case Actions.CurrentFile.Saved(file) =>
          val nowDirty = value.fold(false)(oldFile =>
            oldFile.isDirty && oldFile.value != file)
          updated(
            value.map(
              Dirtying.isDirty
                .set(nowDirty)
                .andThen(Dirtying.nextCleanAction.modify(_.filter(_ =>
                  nowDirty)))),
            value.toOption
              .flatMap(_.nextCleanAction)
              .filter(_ => !nowDirty)
              .map(Effect.action(_))
              .getOrElse(Effect.action(NoAction))
          )
        case Actions.CurrentFile.Loaded(Some(file)) =>
          updated(value.ready(Dirtying(file, isDirty = false)))
        case Actions.CurrentFile.PromptSaveAndThen(next)
            if value.fold(true)(_.isClean) =>
          effectOnly(Effect.action(next))
        case Actions.CurrentFile.PromptSaveAndThen(next) =>
          updated(value.map(Dirtying.nextCleanAction.set(Some(next))))
        case Actions.CurrentFile.PromptSaveIgnore =>
          val action =
            value.toOption.flatMap(_.nextCleanAction).getOrElse(NoAction)
          updated(value.map(Dirtying.nextCleanAction.set(None)),
                  Effect.action(action))
        case Actions.CurrentFile.PromptSaveCancel =>
          updated(value.map(Dirtying.nextCleanAction.set(None)))
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
              val host = dom.window.location.host
              updated(value.pending(),
                      IDEClient.connectToCircuit(s"ws://$host/connect",
                                                 AppCircuit.this))
            case PotPending =>
              noChange
            case PotReady =>
              updated(action.potResult,
                      Effect.action(Actions.Downloaders.Update(Pot.empty)) +
                        Effect.action(Actions.KnownFiles.Update(Pot.empty)))
            case PotUnavailable =>
              updated(value.unavailable())
            case PotFailed =>
              updated(value.fail(action.result.failed.get))
          }
      }
    }

  def filesHandler =
    new ActionHandler[Root, Pot[Seq[SourceFileRef]]](zoomTo(_.knownFiles)) {
      override def handle: PartialFunction[Any, ActionResult[Root]] = {
        case action: Actions.KnownFiles.Update =>
          action.handleWith(this, IDEClient.requestFileList(commandQueue))(
            PotAction.handler())
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
                      IDEClient.requestDownloaderList(commandQueue))
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
          effectOnly(IDEClient.selectDownloader(downloader, commandQueue))
        case Actions.Downloaders.Selected(downloader) =>
          updated(value.map(Downloaders.current.set(downloader)))
        case Actions.Downloaders.AddEvent(event) =>
          updated(value.map(Downloaders.events.modify(event +: _)))
        case Actions.Downloaders.SendInstructions(instructions) =>
          effectOnly(IDEClient.sendBytecode(instructions, commandQueue))
        case Actions.Downloaders.Reset =>
          effectOnly(IDEClient.resetDownloader(commandQueue))
        case Actions.Downloaders.DownloadDone(dlEvent) =>
          effectOnly(
            Effect.action(Actions.Downloaders.AddEvent(dlEvent)) + Effect
              .action(Actions.Downloaders.Reset))
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
        case IDEEvent.KnownFiles(files) =>
          Actions.KnownFiles.Update(Ready(files))
        case IDEEvent.SavedFile(file) =>
          Actions.CurrentFile.Saved(file)
        case IDEEvent.GotFile(file) =>
          Actions.CurrentFile.Loaded(file)
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
        case IDEEvent.FromDownloader(
            dlEvent: DownloaderEvent.DownloadedBytecode) =>
          Actions.Downloaders.DownloadDone(dlEvent)
        case IDEEvent.FromDownloader(dlEvent) =>
          Actions.Downloaders.AddEvent(dlEvent)
      }

      override def handle = {
        case Actions.IDEEvent.Received(event) =>
          effectOnly(Effect.action(toAction(event)))
      }
    }
}
