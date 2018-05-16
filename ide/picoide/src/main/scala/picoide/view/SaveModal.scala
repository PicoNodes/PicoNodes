package picoide.view

import diode.data.Pot
import diode.react.ModelProxy
import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.html_<^._
import picoide.{Actions, Dirtying}
import picoide.proto.SourceFile

import scala.scalajs.js
import org.scalajs.dom

object SaveModal {
  class Backend($ : BackendScope[ModelProxy[Pot[Dirtying[SourceFile]]], Unit]) {
    def registerCloseHook(): Callback = Callback(
      dom.window.onbeforeunload = { (ev: dom.raw.BeforeUnloadEvent) =>
        $.props.map { props =>
          if (props().fold(false)(_.isDirty))
            "You have unsaved data! Are you sure you want to leave?": js.UndefOr[
              String]
          else
            js.undefined
        }.runNow
      }
    )

    def render(props: ModelProxy[Pot[Dirtying[SourceFile]]]) =
      <.div(
        Modal
          .component(
            <.h2("Danger!"),
            <.div(
              "You will lose any unsaved data! Are you sure you want to continue?"),
            <.div(
              ^.className := "save-modal-button-row",
              <.button(
                "Save",
                ^.onClick --> props.dispatchCB(Actions.CurrentFile.Save)),
              <.button("Ignore",
                       ^.onClick --> props.dispatchCB(
                         Actions.CurrentFile.PromptSaveIgnore)),
              Spacer.component(),
              <.button("Cancel",
                       ^.onClick --> props.dispatchCB(
                         Actions.CurrentFile.PromptSaveCancel))
            )
          )
          .when(props().fold(false)(_.nextCleanAction.isDefined)))
  }

  val component =
    ScalaComponent
      .builder[ModelProxy[Pot[Dirtying[SourceFile]]]]("SaveModal")
      .renderBackend[Backend]
      .componentDidMount(_.backend.registerCloseHook())
      .build
}
