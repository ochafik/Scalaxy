package scalaxy.streams

private[streams] trait BuilderSinks extends StreamComponents {
  val global: scala.reflect.api.Universe
  import global._

  // Base class for builder-based sinks.
  trait BuilderSink extends StreamSink
  {
    def createBuilder(inputVars: TuploidValue[Tree], typed: Tree => Tree): Tree

    override def emit(input: StreamInput, outputNeeds: OutputNeeds, nextOps: OpsAndOutputNeeds): StreamOutput =
    {
      import input._

      requireSinkInput(input, outputNeeds, nextOps)

      val builder = fresh("builder")
      require(input.vars.alias.nonEmpty, s"input.vars = $input.vars")

      // println("inputVars.alias.get = " + inputVars.alias.get + ": " + inputVars.tpe)
      val sizeHintOpt = input.outputSize.map(s => q"$builder.sizeHint($s)")
      val Block(List(
          builderDef,
          sizeHint,
          builderAdd,
          result), _) = typed(q"""
        private[this] val $builder = ${createBuilder(input.vars, typed)};
        ${sizeHintOpt.getOrElse(Literal(Constant("")))};
        $builder += ${input.vars.alias.get};
        $builder.result();
        {}
      """)

      StreamOutput(
        prelude = List(builderDef),
        body = input.outputSize.map(_ => sizeHint).toList :+ builderAdd,
        ending = List(result))
    }
  }
}