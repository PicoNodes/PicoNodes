package picoide

import diode.{ActionHandler, Circuit}
import diode.react.ReactConnector

object AppCircuit extends Circuit[Root] with ReactConnector[Root] {
  override def initialModel = Root(
    currentFile = SourceFile("""  mov 1 up
                               |  mov 2 null
                               |  mov 3 acc
                               |
                               |+ mov 4 acc
                               |- mov 6 acc
                               |  mov 5 null
""".stripMargin)
  )

  override def actionHandler = composeHandlers(editorHandler)

  def editorHandler = new ActionHandler(zoomTo(_.currentFile)) {
    override def handle = {
      case Actions.EditCurrentFile(newContent) =>
        updated(value.copy(content = newContent))
    }
  }
}
