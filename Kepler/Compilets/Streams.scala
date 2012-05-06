package scalaxy; package compilets

import macros._

object Streams {
  // TODO add conditions macro + isSideEffectFree(f)
  def mapMap[A : TypeTag, B : TypeTag, C : TypeTag](col: Seq[A], f: A => B, g: B => C) = replace(
    col.map(f).map(g),
    col.map(a => {
      val b = f(a)
      val c = g(b)
      c
    })
  )
}
