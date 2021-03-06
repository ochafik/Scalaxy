/*
 * ScalaCL - putting Scala on the GPU with JavaCL / OpenCL
 * http://scalacl.googlecode.com/
 *
 * Copyright (c) 2009-2013, Olivier Chafik (http://ochafik.com/)
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * Neither the name of Olivier Chafik nor the
 *       names of its contributors may be used to endorse or promote products
 *       derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY OLIVIER CHAFIK AND CONTRIBUTORS ``AS IS'' AND ANY
 * EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE REGENTS AND CONTRIBUTORS BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package scalaxy.components

import scala.reflect.api.Universe

trait StreamTransformers
    extends OpsStreams {
  val global: Universe
  import global._
  import definitions._
  import Flag._

  def stream = true

  def newStreamTransformer(optimizeOnlyIfKnownToBenefit: Boolean) = new Transformer /* TODO: TypingTransformer */ {

    var matchedColTreeIds = Set[Tree]()

    override def transform(tree: Tree): Tree = {
      //val retryWithSmallerChain = false
      //def internalTransform(tree: Tree, retryWithSmallerChain: Boolean) = transform(tree)

      internalTransform(tree)
    }

    protected def internalTransform(
      tree: Tree,
      retryWithSmallerChain: Boolean = true): Tree =
      {
        // println(s"internalTransform($tree)")
        try {
          tree match {
            case ArrayTabulate(componentType, lengths @ (firstLength :: otherLengths), f @ Func(params, body), manifest) =>
              val tpe = body.tpe
              val returnType = //if (tpe.isInstanceOf[ConstantType]) 
                tpe.dealias.widen
              //else
              //  tpe

              val lengthDefs = lengths.map {
                case length =>
                  newVal("n$", typeCheck(length, IntTpe), IntTpe)
              }

              //msg(tree.pos, "transformed Array.tabulate[" + returnType + "] into equivalent while loop") 
              {

                def replaceTabulates(lengthDefs: List[ValueDef], parentArrayIdentGen: IdentGen, params: List[ValDef], mappings: Map[Symbol, TreeGen], symbolReplacements: Map[Symbol, Symbol]): (Tree, Type) = {

                  val param = params.head
                  val pos = tree.pos
                  val nVar = lengthDefs.head
                  val iVar = newVar("i$", newInt(0), IntTpe)
                  val iVal = newVal("i$val$", iVar(), IntTpe)

                  val newMappings: Map[Symbol, TreeGen] = mappings + (param.symbol -> iVal)
                  val newReplacements = symbolReplacements // ++ Map(param.symbol -> iVal())

                  val mappedArrayTpe = getArrayType(returnType, lengthDefs.size)

                  val arrayVar = if (parentArrayIdentGen == null)
                    newVal("m$", newArrayMulti(mappedArrayTpe, returnType, lengthDefs.map(_.identGen()), manifest), mappedArrayTpe)
                  else
                    ValueDef(parentArrayIdentGen, null, null) // TODO pass types here and around

                  val subArrayVar = if (lengthDefs.tail == Nil)
                    null
                  else
                    newVal("subArray$", newApplyCall(arrayVar(), iVal()), getArrayType(returnType, lengthDefs.size - 1))

                  val (newBody, bodyType) = if (lengthDefs.tail == Nil)
                    (
                      replaceOccurrences(
                        body,
                        newMappings,
                        newReplacements,
                        Map()
                      ),
                        returnType
                    )
                  else
                    replaceTabulates(
                      lengthDefs.tail,
                      subArrayVar,
                      params.tail,
                      newMappings,
                      newReplacements
                    )

                  val checkedBody = typeCheck(
                    newBody,
                    bodyType
                  )

                  (
                    super.transform {
                      treeCopy.Block(
                        tree,
                        (
                          if (parentArrayIdentGen == null)
                            lengthDefs.map(_.definition) :+ arrayVar.definition
                          else
                            Nil
                        ) ++
                          List(
                            iVar.definition,
                            whileLoop(
                              binOp(
                                iVar(),
                                LT,
                                nVar()
                              ),
                              Block(
                                (
                                  if (lengthDefs.tail == Nil)
                                    List(
                                    iVal.definition,
                                    newUpdate(
                                      tree.pos,
                                      arrayVar(),
                                      iVar(),
                                      checkedBody
                                    )
                                  )
                                  else {
                                    List(
                                      iVal.definition,
                                      subArrayVar.definition,
                                      checkedBody
                                    )
                                  }
                                ),
                                incrementIntVar(iVar, newInt(1))
                              )
                            )
                          ),
                        if (parentArrayIdentGen == null)
                          arrayVar()
                        else
                          newUnit
                      )
                    },
                    mappedArrayTpe
                  )
                }
                replaceTabulates(lengthDefs, null, params, Map(), Map())._1
              }
            case SomeOpsStream(opsStream) if stream &&
              //(opsStream.source ne null) &&
              //!opsStream.ops.isEmpty &&
              //(opsStream ne null) &&
              (opsStream.colTree ne null) &&
              !matchedColTreeIds.contains(opsStream.colTree) =>
              import opsStream._

              def txt = "Streamed ops on " + (if (source == null) "UNKNOWN COL" else source.tree.tpe) + " :\n\t" + opsStream //ops.mkString(",\n\t")
              matchedColTreeIds += colTree
              // msg(tree.pos, "# " + txt)
              // println(txt)

              {
                try {
                  val stream = Stream(source, ops)
                  if (optimizeOnlyIfKnownToBenefit)
                    checkStreamWillBenefitFromOptimization(stream)
                  val asm = assembleStream(stream, tree, this.transform _, tree.pos, currentOwner)
                  // println(txt + "\n\t" + asm.toString.replaceAll("\n", "\n\t"))
                  // println("### TRANSFORMED : ###\n" + showRaw(asm))
                  asm
                } catch {
                  case ex @ BrokenOperationsStreamException(msg, sourceAndOps, componentsWithSideEffects) =>
                    println("broken: " + ex)
                    warning(sourceAndOps.head.tree.pos, "Cannot optimize this operations stream due to side effects")
                    for (SideEffectFullComponent(comp, sideEffects, preventedOptimizations) <- componentsWithSideEffects) {
                      for (sideEffect <- sideEffects) {
                        if (preventedOptimizations)
                          warning(sideEffect.pos,
                            "This side-effect prevents optimization of the enclosing " + comp + " operation ; node = " + sideEffect //+
                          //(if (verbose) " ; node = " + nodeToString(sideEffect) else "")
                          )
                        else if (verbose)
                          warnSideEffect(sideEffect)
                      }
                      //println("Side effects of " + comp + " :\n\t" + sideEffects.mkString(",\n\t"))
                    }

                    val sub = super.transform(tree)
                    if (retryWithSmallerChain)
                      internalTransform(sub, retryWithSmallerChain = false)
                    else
                      sub
                  case ex: Throwable =>
                    ex.printStackTrace()
                    throw ex
                }
              }
            case _ =>
              super.transform(tree) //toMatch)
          }
        } catch {
          case ex: CodeWontBenefitFromOptimization =>
            if (verbose)
              warning(tree.pos, ex.toString)
            super.transform(tree)
          case ex: Throwable =>
            if (verbose)
              ex.printStackTrace
            super.transform(tree)
        }
      }
  }
  def checkStreamWillBenefitFromOptimization(stream: Stream): Unit = {
    val Stream(source, transformers) = stream

    val sourceAndOps = source +: transformers

    val closuresCount = sourceAndOps.map(_.closuresCount).sum
    (transformers, closuresCount, source) match {
      case (Seq(), _, _) =>
        throw CodeWontBenefitFromOptimization("No operations chain : " + sourceAndOps)
      case (_, _, _: AbstractArrayStreamSource) if !transformers.isEmpty =>
      // ok to transform any stream that starts with an array
      case (Seq(_), 0, _) =>
        throw CodeWontBenefitFromOptimization("Only one operations without closure is not enough to optimize : " + sourceAndOps)
      case (Seq(_), 1, _: ListStreamSource) =>
        throw CodeWontBenefitFromOptimization("List operations chains need at least 2 closures to make the optimization beneficial : " + sourceAndOps)
      case (
        Seq(
          _: FilterWhileOp |
          _: MaxOp |
          _: MinOp |
          _: SumOp |
          _: ProductOp |
          _: ToCollectionOp
          ),
        1,
        _: RangeStreamSource
        ) =>
        throw CodeWontBenefitFromOptimization("This operations stream would not benefit from a while-loop-rewrite optimization : " + sourceAndOps)
      case _ =>
    }
  }
}
