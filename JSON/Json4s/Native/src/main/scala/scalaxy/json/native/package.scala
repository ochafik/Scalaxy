package scalaxy.json.json4s

import scala.language.experimental.macros

import org.json4s._
import org.json4s.native.JsonMethods

package object native extends base.PackageBase {
  import implementation._

  implicit class JSONStringContext(val context: StringContext) {
    object json extends base.ExtractibleJSONStringContext(context) {
      def apply(args: Any*): JValue =
        macro jsonApply

      override def parse(str: String): JValue =
        JsonMethods.parse(str)
    }
  }
}
