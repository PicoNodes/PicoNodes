package picoide

import monocle.Lens
import scala.language.implicitConversions

package object utils {
  implicit def dirtyingLensAdapter[A, B](
      lens: Lens[A, B]): Lens[Dirtying[A], B] =
    Dirtying.value ^|-> lens
}
