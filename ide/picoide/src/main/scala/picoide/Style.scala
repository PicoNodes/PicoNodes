package picoide

import scala.scalajs.js.annotation.JSImport
import scala.scalajs.js

object Style {
  @JSImport("../../../../src/main/resources/style.scss", JSImport.Default)
  @js.native
  private object MainStyle extends js.Object

  def load(): Unit =
    // Any reference to MainStyle causes that style to be loaded by Webpack
    MainStyle
}
