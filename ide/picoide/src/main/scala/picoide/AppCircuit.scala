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
""".stripMargin.lines.toSeq)
  )

  override def actionHandler = composeHandlers(editorHandler)

  def editorHandler = new ActionHandler(zoomTo(_.currentFile)) {
    override def handle = {
      case Actions.CurrentFile.ReplaceLine(line, newContent) =>
        updated(value.copy(content = value.content.updated(line, newContent)))
      case Actions.CurrentFile.SplitLine(line, position) =>
        val (before, current :: after) = value.content.toList.splitAt(line)
        val (lineBefore, lineAfter)    = current.splitAt(position)
        updated(
          value.copy(content = before ++ (lineBefore :: lineAfter :: after)))
    }
  }
}
