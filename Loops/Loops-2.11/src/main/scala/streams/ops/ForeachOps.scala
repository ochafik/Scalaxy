package scalaxy.loops

private[loops] trait ForeachOps
    extends ClosureStreamOps
    with UnusableSinks
{
  val global: scala.reflect.api.Universe
  import global._

  object SomeForeachOp {
    def unapply(tree: Tree): Option[(Tree, ForeachOp)] = Option(tree) collect {
      case q"$target.foreach[$_](${Strip(Function(List(param), body))})" =>
        (target, ForeachOp(param, body))
    }
  }
  case class ForeachOp(param: ValDef, body: Tree)
      extends ClosureStreamOp
  {
    override def describe = Some("foreach")

    override def sinkOption = Some(UnusableSink)

    override def emit(input: StreamInput, outputNeeds: OutputNeeds, nextOps: OpsAndOutputNeeds): StreamOutput =
    {
      val List((UnusableSink, _)) = nextOps

      val (replacedStatements, outputVars) =
        transformationClosure.replaceClosureBody(input, outputNeeds)

      require(outputVars.tpe =:= typeOf[Unit], "Expected Unit, got " + outputVars.tpe)

      // println(s"""
      //   body: $body,
      //   transformationClosure: $transformationClosure,
      //   statements = ${transformationClosure.statements},
      //   replacedStatements = $replacedStatements
      // """)

      StreamOutput(
        prelude = Nil,
        // TODO match params and any tuple extraction in body with streamVars, replace symbols with streamVars values
        body = replacedStatements,
        ending = Nil)
    }
  }
}
