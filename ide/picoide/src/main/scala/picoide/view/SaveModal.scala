package picoide.view

import diode.data.Pot
import diode.react.ModelProxy
import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.html_<^._
import picoide.{Actions, Dirtying}
import picoide.proto.SourceFile

object SaveModal {
  val component =
    ScalaComponent
      .builder[ModelProxy[Pot[Dirtying[SourceFile]]]]("SaveModal")
      .render_P(props =>
        <.div(<.div(
          ^.className := "save-modal",
          <.h2("Danger!"),
          <.div(
            "You will lose any unsaved data! Are you sure you want to continue?"),
          <.button("Save",
                   ^.onClick --> props.dispatchCB(Actions.CurrentFile.Save)),
          <.button("Ignore",
                   ^.onClick --> props.dispatchCB(
                     Actions.CurrentFile.PromptSaveIgnore)),
          <.button("Cancel",
                   ^.onClick --> props.dispatchCB(
                     Actions.CurrentFile.PromptSaveCancel)),
        ).when(props().fold(false)(_.nextCleanAction.isDefined))))
      .build
}
