package scalaxy.loops

private[loops] trait StreamResults extends TuploidValues {
  val global: scala.reflect.api.Universe
  import global._

  type OutputNeeds = Set[TuploidPath]

  case class StreamOutput(
      prelude: List[Tree] = Nil,
      body: List[Tree] = Nil,
      ending: List[Tree] = Nil)
  {
    def compose(typed: Tree => Tree) = typed(q"..${prelude ++ body ++ ending}")
    def map(f: Tree => Tree): StreamOutput =
      copy(prelude = prelude.map(f), body = body.map(f), ending = ending.map(f))
  }

  val NoStreamOutput = StreamOutput(prelude = Nil, body = Nil, ending = Nil)

  case class StreamInput(
    vars: TuploidValue[Tree],
    outputSize: Option[Tree] = None,
    index: Option[Tree] = None,
    fresh: String => TermName,
    transform: Tree => Tree,
    typed: Tree => Tree)
}
