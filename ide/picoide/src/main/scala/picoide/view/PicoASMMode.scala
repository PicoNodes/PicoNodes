package picoide.view

import picoide.view.vendor.ReactCodeMirror

import scala.scalajs.js

object PicoASMMode {
  def register(): Unit =
    ReactCodeMirror.Simple
  ReactCodeMirror.CodeMirror
    .defineSimpleMode(
      "picoasm",
      js.Dictionary(
        "start" -> js.Array(
          new ReactCodeMirror.SimpleRule(
            regex = js.RegExp("(left|right|up|down)(?![a-zA-Z])"),
            token = "register-io"),
          new ReactCodeMirror.SimpleRule(regex =
                                           js.RegExp("(acc|null)(?![a-zA-Z])"),
                                         token = "register-mem"),
          new ReactCodeMirror.SimpleRule(regex = js.RegExp("[+-]"),
                                         token = "flags"),
          new ReactCodeMirror.SimpleRule(
            regex = js.RegExp("(mov|add|sub|teq|tgt|tlt|tcp)(?![a-zA-Z])"),
            token = "instruction"),
          new ReactCodeMirror.SimpleRule(regex = js.RegExp("[0-9]+"),
                                         token = "literal-int"),
          new ReactCodeMirror.SimpleRule(regex = js.RegExp("[a-zA-Z]+"),
                                         token = "invalid")
        ))
    )
}
