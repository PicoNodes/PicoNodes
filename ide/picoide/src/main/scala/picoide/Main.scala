package picoide

import org.scalajs.dom.document
import picoide.view.EditorView

object Main {
  def main(args: Array[String]): Unit = {
    Style.load()
    EditorView.component
      .renderIntoDOM(document.getElementById("main-container"))
  }
}
