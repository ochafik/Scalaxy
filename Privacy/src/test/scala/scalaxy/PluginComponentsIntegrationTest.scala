package scalaxy.privacy.test

import scala.language.existentials

import scala.tools.nsc.Global

import scalaxy.privacy.PrivacyPlugin
import scala.tools.nsc.reporters.{ StoreReporter, Reporter }
import org.junit._
import org.junit.Assert._

class PluginComponentsIntegrationTest extends TestBase {

  override def getInternalPhases(global: Global) =
    PrivacyPlugin.getInternalPhases(global)

  @Test
  def mixAllFeatures {
    assertEquals(
      List(
        "Public member `ff` with non-trivial value should have a explicit type annotation",
        "Public member `ffff` with non-trivial value should have a explicit type annotation",
        "Public member `g` with non-trivial value should have a explicit type annotation",
        "Public member `vvv` with non-trivial value should have a explicit type annotation",
        "Public member `barf` with non-trivial value should have a explicit type annotation",
        "Public member `barv` with non-trivial value should have a explicit type annotation",
        "Public member `barvv` with non-trivial value should have a explicit type annotation"
      ),
      compile("""
        @public class Foo {
          @public def f = 10
          @public def ff = f

          // These ones are private, so no need for type annotation
          def fff = ff
          val v = 10
          val vv = v

          @public def ffff = fff
          @public def g(x: Int) = if (x < 0) 1 else 2
          @public val vvv = vv

          @noprivacy class Bar {
            // Normal Scala visibility rules apply here.

            def barf(x: Int) = if (x < 0) 1 else 2
            private def barg(x: Int) = barf(x)

            val barv = barf(10)
            val barvv = barv
            private val barvvv = barvv
          }
        }
      """)
    )
  }
}
