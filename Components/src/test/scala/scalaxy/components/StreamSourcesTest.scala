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

import org.junit._
import Assert._
import org.hamcrest.CoreMatchers._

class StreamSourcesTest
    extends StreamSources
    with WithRuntimeUniverse
    with WithTestFresh {
  import global._
  import definitions._

  object const {
    def unapply(t: Tree) = Option(t) collect {
      case Literal(Constant(v)) => v
    }
  }

  @Test def intRangeSource {
    val StreamSource(RangeStreamSource(_, const(0), const(10), 1, true, IntTpe)) =
      typeCheck(reify(0 until 10))
  }
  @Test def longRangeSource {
    val StreamSource(RangeStreamSource(_, const(0L), const(10L), 1, true, LongTpe)) =
      typeCheck(reify(0L until 10L))
  }

  @Test def applyArraySource {
    val StreamSource(ArrayApplyStreamSource(_, _, _)) =
      typeCheck(reify(Array(1, 2)))
  }

  @Test def applySeqSource {
    val StreamSource(SeqApplyStreamSource(_, _, _)) =
      typeCheck(reify(Seq(1, 2)))
  }

  @Test def applyListSource {
    val StreamSource(ListApplyStreamSource(_, _, _)) =
      typeCheck(reify(List(1, 2)))
  }

  @Test def listSource {
    val StreamSource(ListStreamSource(_, _)) =
      typeCheck(reify({ val x = 1 :: Nil; x }))
  }

  @Test def applyOptionSource {
    val StreamSource(OptionStreamSource(_, Some(_), true, _)) =
      typeCheck(reify(Option(1)))
  }

  @Test def optionSource {
    val StreamSource(OptionStreamSource(_, None, true, _)) =
      typeCheck(reify({ val x = Option(1); x }))
  }

  @Ignore
  @Test def applyIndexedSeqSource {
    val StreamSource(IndexedSeqApplyStreamSource(_, _, _)) =
      typeCheck(reify(IndexedSeq(1, 2)))
  }

  @Ignore
  @Test def applyVectorSource {
    val StreamSource(VectorApplyStreamSource(_, _, _)) =
      typeCheck(reify(Vector(1, 2)))
  }

  @Ignore
  @Test def wrappedArraySource {
    val StreamSource(WrappedArrayStreamSource(_, _, _)) =
      typeCheck(reify(Array(1, 2): Seq[Int]))
  }
}
