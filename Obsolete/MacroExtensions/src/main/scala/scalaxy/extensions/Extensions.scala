// Author: Olivier Chafik (http://ochafik.com)
// Feel free to modify and reuse this for any purpose ("public domain / I don't care").
package scalaxy.extensions

import scala.collection.mutable

//import scala.reflect.api.Universe
import scala.tools.nsc.Global
import scala.reflect.internal.Flags

trait Extensions
{
  val global: Global
  import global._
  import definitions._
  
  class DefsTransformer extends Transformer {
    var parents: List[DefTree] = Nil
    override def transform(tree: Tree) = tree match {
      case dt: DefTree =>
        enterDefTree(dt)
        val res = super.transform(tree)
        leaveDefTree(dt)
        res
      case _ =>
        super.transform(tree)
    }
    def enterDefTree(dt: DefTree) {
      parents = dt :: parents
    }
    def leaveDefTree(dt: DefTree) {
      parents = parents.tail
    }
  }

  
  class DefsTraverser extends Traverser {
    var parents: List[DefTree] = Nil
    override def traverse(tree: Tree) = tree match {
      case dt: DefTree =>
        enterDefTree(dt)
        super.traverse(tree)
        leaveDefTree(dt)
      case _ =>
        super.traverse(tree)
    }
    def enterDefTree(dt: DefTree) {
      parents = dt :: parents
    }
    def leaveDefTree(dt: DefTree) {
      parents = parents.tail
    }
  }

  def termPath(path: String): Tree = {
    termPath(path.split("\\.").toList)
  }
  def termPath(components: List[String]): Tree = {
    components.tail.foldLeft(Ident(components.head: TermName): Tree)((p, n) => Select(p, n: TermName))
  }
  def termPath(root: Tree, path: String): Tree = {
    val components = path.split("\\.").toList
    components.foldLeft(root)((p, n) => Select(p, n: TermName))
  }

  def typePath(path: String): Tree = {
    val components = path.split("\\.")
    Select(termPath(components.dropRight(1).toList), components.last: TypeName)
  }

  def newImportAll(tpt: Tree, pos: Position): Import = {
    Import(
      tpt,
      List(
        ImportSelector("_": TermName, pos.point, null, -1)))
  }

  def newImportMacros(pos: Position): Import = {
    val macrosName: TermName = "macros"
    Import(
      termPath("scala.language.experimental"),
      List(
        ImportSelector(macrosName, pos.point, macrosName, -1)
      )
    )
  }

  def newEmptyTpt() = TypeTree(null)

  def getTypeNames(tpt: Tree): Seq[TypeName] = {
    val res = mutable.ArrayBuffer[TypeName]()
    new Traverser { override def traverse(tree: Tree) = tree match {
      case Ident(n: TypeName) => res += n
      case _ => super.traverse(tree)
    }}.traverse(tpt)
    res.result()
  }
  
  def newExprType(contextName: TermName, tpt: Tree) = {
    AppliedTypeTree(
      typePath(contextName + ".Expr"),
      List(tpt))
  }
  def newExpr(contextName: TermName, tpt: Tree, value: Tree) = {
    Apply(
      TypeApply(
        termPath(contextName + ".Expr"),
        List(tpt)),
      List(value))
  }
  def newSplice(name: String) = {
    Select(Ident(name: TermName), "splice": TermName)
  }
  
  def genParamAccessorsAndConstructor(namesAndTypeTrees: List[(String, Tree)]): List[Tree] = {
    (
      namesAndTypeTrees.map {
        case (name, tpt) =>
          ValDef(Modifiers(Flags.PARAMACCESSOR), name, tpt, EmptyTree)
      }
    ) :+
    DefDef(
      NoMods,
      termNames.CONSTRUCTOR,
      Nil,
      List(
        namesAndTypeTrees.map { case (name, tpt) =>
          ValDef(NoMods, name, tpt, EmptyTree)
        }
      ),
      newEmptyTpt(),
      newSuperInitConstructorBody()
    )
  }

  def newSelfValDef(): ValDef = {
    ValDef(Modifiers(Flag.PRIVATE), "_": TermName, newEmptyTpt(), EmptyTree)
  }

  def newSuperInitConstructorBody(): Tree = {
    Block(
      // super.<init>()
      Apply(Select(Super(This("": TypeName), "": TypeName), termNames.CONSTRUCTOR), Nil),
      Literal(Constant(()))
    )
  }

  lazy val anyValTypeNames =
    Set("Int", "Long", "Short", "Byte", "Double", "Float", "Char", "Boolean", "AnyVal")

  def parentTypeTreeForImplicitWrapper(typeName: Name): Tree = {
    // If the type being extended is an AnyVal, make the implicit class a value class :-)
    typePath(
      if (anyValTypeNames.contains(typeName.toString))
        "scala.AnyVal"
      else
        "scala.AnyRef"
    )
  }

  // TODO: request some -- official API in scala.reflect.api.FlagSets#FlagOps
  implicit class FlagOps2(flags: FlagSet) {
    def --(others: FlagSet) = {
      //(flags.asInstanceOf[Long] & ~others.asInstanceOf[Long]).asInstanceOf[FlagSet]
      flags & ~others
    }
  }
}
