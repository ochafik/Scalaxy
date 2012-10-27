package scalaxy; package components

import scala.tools.nsc.plugins.PluginComponent
import scala.tools.nsc.symtab.Flags._

import scala.reflect._
import Function.tupled

trait MirrorConversions
extends PatternMatchers
{
  this: PluginComponent =>

  import global.definitions._
  
  private def ultraLogConversions(txt: => String) {
    //println(txt)
  }
  
  private def tryAndType(tree: global.Tree) = {
    try {
      global.typer.typed { tree }
    } catch { case _: Throwable => }
    tree
  }
  
  /**
   * TODO report missing API : scala.reflect.api.SymbolTable 
   * (scala.reflect.mirror does not extend scala.reflect.internal.SymbolTable publicly !)
   */
  def newMirrorToGlobalImporter(mirror: api.Universe)(bindings: Bindings, typeParams: Seq[mirror.Type]) = {
    new global.StandardImporter {
      val from = mirror.asInstanceOf[scala.reflect.internal.SymbolTable]
      
      //override def importSymbol(sym: from.Symbol) = {
      //  println("IMPORT SYMBOL " + sym)
      //  val imp = if (tpe == null)
      //    null
      //  else {
      //    lazy val it = resolveType(global)(super.importType(tpe))
      //    bindings.getType(it.asInstanceOf[patternUniv.Type]).getOrElse(it).asInstanceOf[global.Type]
      //  }
      //  /*val imp =
      //  try {
      //    importType(sym.asType.toType).typeSymbol
      //  } catch { case _ =>
      //    super.importSymbol(sym)
      //  }*/
      //  println("-> SYMBOL " + imp)
      //  imp
      //}
      override def importTree(tree: from.Tree) = {
        ultraLogConversions("IMPORT TREE (tpe = " + tree.tpe + ", cls = " + tree.getClass.getName + "): " + tree)
        val imp = tree match {
          case from.Ident(n) =>
            bindings.nameBindings.get(n.toString).getOrElse(super.importTree(tree)).asInstanceOf[global.Tree]
              
          case _ =>
            super.importTree(tree)
        }
        ultraLogConversions("-> TREE " + imp)
        
        imp
      }
      override def importType(tpe: from.Type): global.Type = {
        ultraLogConversions("IMPORT TYPE " + tpe + " (typeParams = " + typeParams + ")")
        //val typeParamIndex: Option[Int] = TypeVars.typeVarIndex(from)(tpe)
        //val typeParamIndex = typeParams.indexOf(tpe)
        val imp = if (tpe == null) {
          null
        } /*else if (typeParamIndex >= 0) {//!= None) {
          //val i = typeParamIndex//.get
          //if (i >= typeParams.size)
          //  throw new RuntimeException("typeParams = " + typeParams + ",  i = " + i + ", t = " + tpe)
          super.importType(typeParams(i).asInstanceOf[from.Type])
        } */else {
          val rtpe = resolveType(from)(tpe)
          val it = resolveType(global)(super.importType(rtpe))
          //TODO? 
          //bindings.getType(it.asInstanceOf[patternUniv.Type]).getOrElse(it).asInstanceOf[global.Type]
          bindings.getType(rtpe.asInstanceOf[patternUniv.Type]).getOrElse(it).asInstanceOf[global.Type]
        }
        ultraLogConversions("-> TYPE " + imp)
        imp
      }
    }
  }
  
  def newGlobalToMirrorImporter(mirror: api.Universe) = {
    val mm = mirror.asInstanceOf[scala.reflect.internal.SymbolTable]
    new mm.StandardImporter {
      val from = global//.asInstanceOf[scala.reflect.internal.SymbolTable]
      /*override def importTree(tree: from.Tree): mm.Tree = tree match {
        case from.Ident(n) =>
          val imp = mm.Ident(importName(n)) ; imp.tpe = importType(tree.tpe) ; imp
        case _ =>
          super.importTree(tree)
      }*/
    }
  }
  
  //@deprecated("dying code")
  //private def fixMissingMirrorTypes(m: mirror.Tree) = {
  //  new mirror.Traverser {
  //    override def traverse(t: mirror.Tree) = {
  //      val tpe = t.tpe
  //      if (tpe == null || tpe == mirror.NoType) {
  //        val sym = t.symbol
  //        if (sym != null && sym != mirror.NoSymbol) {
  //          t.tpe = sym.asType
  //        }
  //      }
  //      super.traverse(t)
  //    }
  //  }.traverse(m)
  //}
  
  /**
    scala.reflect.mirror
    scala.reflect.runtime.universe
  */
  def mirrorToGlobal(mirror: api.Universe)(m: mirror.Tree, bindings: Bindings, typeParams: Seq[mirror.Type]): global.Tree = {
    val importer =
      newMirrorToGlobalImporter(mirror)(bindings, typeParams)
    //fixMissingMirrorTypes(m)
    try {
      importer.importTree(m.asInstanceOf[importer.from.Tree])
    } catch { case ex: Throwable => 
      ultraLogConversions("FAILED importer.importTree(" + m + "):\n\t" + ex)
      throw ex
    }
  }
  
  def globalToMirror(mirror: api.Universe)(t: global.Tree): mirror.Tree = {
    val importer = newGlobalToMirrorImporter(mirror)
    importer.importTree(t.asInstanceOf[importer.from.Tree]).asInstanceOf[mirror.Tree]
  }
  
  /*
  def importName(from: api.Universe, to: api.Universe)(n: from.Name): to.Name =
    n match { 
      case _: from.TermName =>
        to.newTermName(n.toString)
      case _: from.TypeName =>
        to.newTypeName(n.toString)
    }
    
  def globalToMirror(t: global.Name): mirror.Name = {
    importName(global, mirror)(t)
  }
  */
}
