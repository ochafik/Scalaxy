package scalaxy.loops

import scala.reflect.NameTransformer.{ encode, decode }

private[loops] trait ZipWithIndexOps
  extends TransformationClosures
  with CanBuildFromSinks
{
  val global: scala.reflect.api.Universe
  import global._

  object SomeZipWithIndexOp {
    def unapply(tree: Tree): Option[(Tree, ZipWithIndexOp)] = Option(tree) collect {
      case q"$target.zipWithIndex[$_, $_]($canBuildFrom)" =>
        (target, ZipWithIndexOp(canBuildFrom))
    }
  }

  case class ZipWithIndexOp(canBuildFrom: Tree) extends StreamOp
  {
    override def describe = Some("zipWithIndex")

    override val sinkOption = Some(CanBuildFromSink(canBuildFrom))

    override def transmitOutputNeedsBackwards(paths: Set[TuploidPath]) = {
      paths collect {
        // Only transmit _._1 and its children backwards
        case 0 :: sub =>
          sub
      }
    }

    override def emitOp(
        inputVars: TuploidValue[Tree],
        outputNeeds: Set[TuploidPath],
        opsAndOutputNeeds: List[(StreamOp, Set[TuploidPath])],
        fresh: String => TermName,
        transform: Tree => Tree): StreamOpOutput =
    {
      // TODO wire input and output fiber vars 
      val indexVar = fresh("indexVar")
      val indexVal = fresh("indexVal")

      val needsPair: Boolean = outputNeeds(RootTuploidPath)
      val pairName: TermName = if (needsPair) fresh("zipWithIndexPair") else ""

      // Early typing / symbolization.
      val Block(List(
          indexVarDef,
          indexVarRef,
          indexVarIncr,
          indexValDef,
          indexValRef,
          pairDef,
          pairRef), _) = typed(q"""
        private[this] var $indexVar = 0;
        $indexVar;
        $indexVar += 1;
        private[this] val $indexVal = $indexVar;
        $indexVal;
        private[this] val $pairName = (${inputVars.alias.get}, $indexVal);
        $pairName;
        ()
      """)

      import compat._
      val TypeRef(pre, sym, List(_, _)) = typeOf[(Int, Int)]
      val tupleTpe = TypeRef(pre, sym, List(inputVars.tpe, typeOf[Int]))
      require(tupleTpe != null && tupleTpe != NoType)
      val outputVars =
        TupleValue[Tree](
          tupleTpe,
          Map(
            0 -> inputVars,
            1 -> ScalarValue(typeOf[Int], alias = Some(indexValRef))),
          alias = Some(pairRef))

      val StreamOpOutput(streamPrelude, streamBody, streamEnding) =
        emitSub(outputVars, opsAndOutputNeeds, fresh, transform)

      StreamOpOutput(
        // TODO pass source collection to canBuildFrom if it exists.
        prelude = streamPrelude :+ indexVarDef,
        // TODO match params and any tuple extraction in body with streamVars, replace symbols with streamVars values
        body = List(q"""
          $indexValDef;
          ..${if (needsPair) List(pairDef) else Nil}
          ..$streamBody;
          $indexVarIncr
        """),
        ending = streamEnding
      )
    }
  }
}
