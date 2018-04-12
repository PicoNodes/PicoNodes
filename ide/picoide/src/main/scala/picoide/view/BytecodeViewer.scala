package picoide.view

import diode.react.ModelProxy
import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.html_<^._
import picoide.asm.PicoAsmParser
import picoide.{Actions, SourceFile}

object BytecodeViewer {
  val component =
    ScalaComponent
      .builder[ModelProxy[SourceFile]]("BytecodeViewer")
      .render_P { (file: ModelProxy[SourceFile]) =>
        val parser = new PicoAsmParser
        <.div(
          <.h1("Bytecode"),
          parser.parseAll(parser.instructions, file().content) match {
            case parser.Success(result, _) =>
              <.ol(
                result
                  .map(_.toString())
                  .map(<.li(_)): _*
              )
            case parser.Failure(error, _) =>
              <.pre(error)
            case parser.Error(error, _) =>
              <.pre(error)
          }
        )
      }
      .build
}
