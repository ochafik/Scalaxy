/*
 * Created by IntelliJ IDEA.
 * User: ochafik
 * Date: 10/05/11
 * Time: 15:10
 */
package scalaxy.components

import scala.language.implicitConversions
import scala.language.postfixOps

import scala.reflect.api.Universe

trait Streams
    extends TreeBuilders
    with TupleAnalysis
    with CodeAnalysis {
  val global: Universe
  import global._
  import definitions._

  def warning(pos: Position, msg: String): Unit

  trait LocalContext {
    def currentOwner: Symbol
    def addDefinition(tree: Tree): Unit
  }
  case class Loop(pos: Position, currentOwner: Symbol, transform: Tree => Tree) {
    type OptTreeGen = () => Option[Tree]
    class TreeGenList {
      var data = Seq[Either[Tree, OptTreeGen]]()
      def +=(tree: Tree) =
        data ++= Seq(Left(tree))

      def ++=(trees: Seq[Tree]) =
        data ++= trees.map(Left(_))

      def +=(treeGen: OptTreeGen) =
        data ++= Seq(Right(treeGen))

      def +=(treeGenOpt: Option[TreeGen]) =
        for (treeGen <- treeGenOpt)
          data ++= Seq(Right(() => Some(treeGen())))

      def toSeq: Seq[Tree] = data flatMap (_ match {
        case Left(tree) => Some(tree)
        case Right(treeGen) => treeGen()
      })
      def toList = toSeq.toList
    }
    val preOuter = new TreeGenList
    val tests = new TreeGenList

    class Inners {
      val pre = new TreeGenList
      val core = new TreeGenList
      val post = new TreeGenList

      def toList =
        pre.toList ++ core.toList ++ post.toList
    }
    protected val rootInners = new Inners
    protected var inners = rootInners

    def innerIf(cond: TreeGen) =
      innerComposition(sub => {
        // typeCheck(
        If(
          cond(),
          Block(sub, EmptyTree),
          EmptyTree
        )
        //   ,
        //   UnitTpe
        // )
      })

    def innerComposition(composer: List[Tree] => Tree) = {
      val sub = new Inners
      inners.core += (() => Some(composer(sub.toList)))
      inners = sub
    }
    def preInner = inners.pre
    def inner = inners.core
    var isLoop = true
    def postInner = inners.post

    //val preInner = new TreeGenList
    //val inner = new TreeGenList
    //val postInner = new TreeGenList

    val postOuter = new TreeGenList

    class SubContext(list: TreeGenList) extends LocalContext {
      override def currentOwner = Loop.this.currentOwner
      override def addDefinition(tree: Tree) =
        list += tree
    }
    val innerContext = new SubContext(preInner)
    val outerContext = new SubContext(preOuter)

    def tree: Tree = {
      val postOuterSeq = postOuter.toSeq
      val (postStats, postVal) =
        if (postOuterSeq.isEmpty)
          (Seq(), EmptyTree)
        else
          (postOuterSeq.dropRight(1), postOuterSeq.last)

      val cond = tests.toSeq.reduceLeft(boolAnd)
      val body = //typeCheck(
        Block(rootInners.toList, EmptyTree) //,
      //   UnitTpe
      // )
      val ret = Block(
        preOuter.toList ++
          Seq(
            if (isLoop)
              whileLoop(cond, body)
            else
              newIf(cond, body)
          ) ++
            postStats,
        postVal
      )
      typed { ret }
    }
  }
  trait TupleValue extends (() => Ident) {
    def apply(): Ident
    def tpe: Type
    def elements: Seq[IdentGen]
    def fibersCount: Int
    def componentsCount: Int

    def tuple(implicit context: LocalContext): IdentGen =
      subValue(0, fibersCount)

    def fiber(index: Int)(implicit context: LocalContext): IdentGen =
      subValue(index, 1)

    def subValue(fiberOffset: Int, fiberLength: Int)(implicit context: LocalContext): IdentGen
    def subTuple(fiberOffset: Int, fiberLength: Int)(implicit context: LocalContext): TupleValue
  }
  class DefaultTupleValue(val tpe: Type, val elements: IdentGen*) extends TupleValue {
    if (elements.isEmpty && tpe != UnitTpe)
      throw new RuntimeException("Invalid elements with tpe " + tpe + " : " + elements.mkString(", "))

    def this(vd: ValueDef) = this(vd.tpe, vd)

    override def apply(): Ident = {
      if (elements.size != 1)
        throw new UnsupportedOperationException("TODO tpe " + tpe + ", elements = " + elements.mkString(", "))
      else
        elements.head.apply()
    }
    val tupleInfo = getTupleInfo(tpe)
    def fibersCount = tupleInfo.componentSize
    def componentsCount = elements.size

    protected def hasOneFiber =
      fibersCount == 1 && elements.size == 1

    def subValue(fiberOffset: Int, fiberLength: Int)(implicit context: LocalContext): IdentGen =
      if (fiberLength == 1 && fiberOffset == 0 && hasOneFiber)
        elements(0)
      else if (fiberLength == fibersCount && fiberOffset == 0 && elements.size == 1)
        elements.head
      else
        throw new RuntimeException("not implemented : fibersCount = " + fibersCount + ", fiberOffset = " + fiberOffset + ", fiberLength = " + fiberLength + ", tpe = " + tpe + ", elements = " + elements.map(_()).mkString(", "))
    def subTuple(fiberOffset: Int, fiberLength: Int)(implicit context: LocalContext): TupleValue =
      if (fiberLength == fibersCount && fiberOffset == 0)
        this
      else
        throw new RuntimeException("not implemented")
  }

  implicit def valueDef2TupleValue(value: ValueDef) =
    new DefaultTupleValue(Option(value.tpe).getOrElse(value.definition.tpe), value)

  case class StreamValue(
      value: TupleValue,
      extraFirstValue: Option[TupleValue] = None,
      valueIndex: Option[TreeGen] = None,
      valuesCount: Option[TreeGen] = None) {
    def tpe = value.tpe
    def withoutSizeInfo = copy(valueIndex = None, valuesCount = None)
  }

  sealed trait TraversalDirection
  case object FromLeft extends TraversalDirection
  case object FromRight extends TraversalDirection

  sealed trait Order
  case object SameOrder extends Order
  case object ReverseOrder extends Order
  case object Unordered extends Order

  sealed trait ResultKind
  case object NoResult extends ResultKind
  case object ScalarResult extends ResultKind
  case object StreamResult extends ResultKind

  case class CanChainResult(canChain: Boolean, reason: Option[String])
  trait StreamChainTestable {
    def consumesExtraFirstValue: Boolean = false
    def producesExtraFirstValue: Boolean = false
    def privilegedDirection: Option[TraversalDirection] = None

    def canChainAfter(previous: StreamChainTestable, privilegedDirection: Option[TraversalDirection]) = {
      //println("previous.producesExtraFirstValue = " + previous.producesExtraFirstValue + ", this.consumesExtraFirstValue = " + consumesExtraFirstValue)
      if (previous.producesExtraFirstValue && !consumesExtraFirstValue)
        CanChainResult(false, Some("Operation " + this + " cannot consume the extra first value produced by " + previous))
      else if (privilegedDirection != None && privilegedDirection != this.privilegedDirection)
        CanChainResult(false, Some("Operation " + this + " has a privileged direction incompatible with " + privilegedDirection))
      else
        CanChainResult(true, None)
    }
  }

  class SideEffectsAnalyzer {

    def analyzeSideEffects(base: Tree, trees: Tree*): SideEffects = {
      val flagger = createSideEffectsEvaluator(base, cached = false)
      trees.map(flagger.evaluate(_)).foldLeft(sideEffectFreeAnalysis)(_ ++ _)
    }

    def sideEffectFreeAnalysis: SideEffects =
      Seq()

    def isSideEffectFree(analysis: SideEffects): Boolean =
      analysis.isEmpty
  }
  trait SideEffectFreeStreamComponent extends StreamComponent {
    override def analyzeSideEffectsOnStream(analyzer: SideEffectsAnalyzer) =
      analyzer.sideEffectFreeAnalysis
  }

  trait StreamComponent extends StreamChainTestable {
    def tree: Tree

    def closuresCount = 0

    /// Used to chain stream detection : give the unwrapped content of the tree
    def unwrappedTree = tree
    def analyzeSideEffectsOnStream(analyzer: SideEffectsAnalyzer): SideEffects
  }
  trait CanCreateStreamSink extends StreamChainTestable {
    override def consumesExtraFirstValue: Boolean = true

    def createStreamSink(expectedType: Type, componentTpe: Type, outputSize: Option[TreeGen]): StreamSink
  }
  trait StreamSource extends StreamComponent {
    def emit(direction: TraversalDirection)(implicit loop: Loop): StreamValue
  }
  trait StreamTransformer extends StreamComponent {
    def order: Order
    def reverses = false
    def resultKind: ResultKind = StreamResult

    def transform(value: StreamValue)(implicit loop: Loop): StreamValue
  }
  trait StreamSink extends SideEffectFreeStreamComponent {
    def output(value: StreamValue, expectedType: Type)(implicit loop: Loop): Unit
  }
  case class Stream(
    source: StreamSource,
    transformers: Seq[StreamTransformer])
  case class SideEffectFullComponent(
    component: StreamComponent,
    sideEffects: SideEffects,
    preventedOptimizations: Boolean)
  case class CodeWontBenefitFromOptimization(reason: String)
    extends UnsupportedOperationException(reason)

  case class BrokenOperationsStreamException(
    msg: String,
    sourceAndOps: Seq[StreamComponent],
    componentsWithSideEffects: Seq[SideEffectFullComponent]) extends UnsupportedOperationException(msg)

  def warnSideEffect(tree: Tree) = {
    warning(tree.pos, "Beware of side-effects in operations streams." + (if (verbose) " (" + tree + ")" else ""))
  }
  def assembleStream(stream: Stream, outerTree: Tree, transform: Tree => Tree, pos: Position, currentOwner: Symbol): Tree = {
    val Stream(source, transformers) = stream

    val sourceAndOps = source +: transformers

    val sinkCreatorOpt =
      if (transformers.last.resultKind == StreamResult)
        sourceAndOps.collect({ case ccss: CanCreateStreamSink => ccss }).lastOption match {
          case Some(sinkCreator) =>
            Some(sinkCreator)
          case _ =>
            throw new UnsupportedOperationException("Failed to find any CanCreateStreamSink instance in source ++ ops = " + sourceAndOps + " !")
        }
      else
        None

    implicit val loop = new Loop(pos, currentOwner, transform)
    var direction: Option[TraversalDirection] = None // TODO choose depending on preferred directions...

    val analyzer = new SideEffectsAnalyzer

    val brokenChain =
      sourceAndOps.
        map(comp => (comp, comp.analyzeSideEffectsOnStream(analyzer))).
        dropWhile(_._2.isEmpty)

    val componentsWithSideEffects = brokenChain.filter(!_._2.isEmpty)

    if (brokenChain.size > 1) {
      throw BrokenOperationsStreamException(
        "Operations stream broken by side-effects",
        sourceAndOps,
        componentsWithSideEffects.zipWithIndex.map({
          case ((comp, se), i) =>
            val prevented = i != componentsWithSideEffects.size - 1
            SideEffectFullComponent(comp, se, prevented)
        })
      )
    }
    if (verbose)
      for ((comp, sideEffects) <- componentsWithSideEffects; sideEffect <- sideEffects)
        warnSideEffect(sideEffect)

    for (Seq(a, b) <- (sourceAndOps ++ sinkCreatorOpt.toSeq).sliding(2, 1)) {
      val CanChainResult(canChain, reason) = b.canChainAfter(a, direction)
      if (!canChain) {
        throw new UnsupportedOperationException("Cannot chain streams" + reason.map(" : " + _).getOrElse("."))
      }
      direction = b.privilegedDirection.orElse(direction)
    }

    var value = source.emit(direction.getOrElse(FromLeft))

    for (transformer <- transformers)
      value = transformer.transform(value)

    for (sinkCreator <- sinkCreatorOpt) {
      val expectedType = outerTree.tpe //sourceAndOps.last.tree.tpe
      val sink = sinkCreator.createStreamSink(expectedType, value.value.tpe, value.valuesCount)
      sink.output(value, expectedType)
    }
    // typeCheck(
    loop.tree //,
    //   sourceAndOps.last.tree.tpe
    // )
  }
}
