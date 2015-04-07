Collection of Scala Macro goodies ([BSD-licensed](./LICENSE))


- *[Streams](https://github.com/nativelibs4java/scalaxy-streams)* (*MIGRATED TO ITS OWN REPO*) provide a macro and a compiler plugin that optimize streamed collection operations / for comprehensions by rewriting them to equivalent while loops (Scala 2.11.x):

    ```scala
    for (i <- 0 to n;
         ii = i * i;
         j <- i to n;
         jj = j * j;
         if (ii - jj) % 2 == 0;
         k <- (i + j) to n)
      yield { (ii, jj, k) }
    ```

- *[Loops](https://github.com/nativelibs4java/Scalaxy/tree/master/Loops)* provide a macro that optimizes simple foreach loops on Ranges by rewriting them to equivalent while loops (Scala 2.10.x):

    ```scala
    import scalaxy.loops._
    
    for (i <- 0 until 100000000 optimized) { ... }
    ```
    (special case of / superseeded by Streams below)

- *[JSON](https://github.com/nativelibs4java/Scalaxy/tree/master/JSON)* ([ScalaDoc](http://ochafik.github.io/Scalaxy/JSON/latest/api/index.html)) provides macro-based `json` string interpolation with smart error reporting, compile-time renormalization, deconstruction and more.

- *[Parano](https://github.com/nativelibs4java/scalaxy-parano)* (*MIGRATED TO ITS OWN REPO*) provides compile-time checks to avoid common naming mistakes (ambiguous or swapped case class field extractor names, ambiguous unnamed param names with same type...)

- *[Privacy](https://github.com/nativelibs4java/Scalaxy/tree/master/Privacy)* changes the default member visibily from public to private (unless the `@public` annotation is used)

- *[Beans](https://github.com/nativelibs4java/Scalaxy/tree/master/Beans)* ([ScalaDoc](http://ochafik.github.io/Scalaxy/Beans/latest/api/index.html)) are a nifty combination of Dynamics and macros that provide a type-safe eye-candy syntax to set fields of regular Java Beans in a Scala way (without any runtime dependency at all!):

    ```scala
    import scalaxy.beans._
    
    new MyBean().set(foo = 10, bar = 12)
    ```

- *[Fx](https://github.com/nativelibs4java/Scalaxy/tree/master/Fx)* ([ScalaDoc](http://ochafik.github.io/Scalaxy/Fx/latest/api/index.html)) contains an experimental JavaFX DSL (with no runtime dependency) that makes it easy to build objects and define event handlers:

    ```scala
    new Button().set(
      text = bind {
        s"Hello, ${textField.getText}"
      },
      onAction = {
        println("Hello World!")
      }
    )
    ```

- *[Reified](https://github.com/nativelibs4java/scalaxy-reified)*  (*MIGRATED TO ITS OWN REPO*) provides a powerful reified values mechanism that deals well with composition and captures of runtime values, allowing for complex ASTs to be generated during runtime for re-compilation or transformation purposes. It preserves the original value that was reified, allowing for flexible mixed usage of runtime value and compile-time AST.

    ```scala
    import scalaxy.reified._
    
    def comp(capture1: Int): ReifiedFunction1[Int, Int] = {
      val capture2 = Seq(10, 20, 30)
      val f = reified((x: Int) => capture1 + capture2(x))
      val g = reified((x: Int) => x * x)
      
      g.compose(f)
    }
    
    println("AST: " + comp(10).expr.tree)
    ```

- Obsolete experiments (mostly because of quasiquotes):

  - *[MacroExtensions](https://github.com/nativelibs4java/Scalaxy/tree/master/Obsolete/MacroExtensions)* provides an extremely simple (and *experimental*) syntax to define extensions methods as macros:

      ```scala
      @scalaxy.extension[Any] 
      def quoted(quote: String): String = 
        quote + self + quote
        
      @scalaxy.extension[Int] 
      def copiesOf[T : ClassTag](generator: => T): Array[T] = 
        Array.fill[T](self)(generator)
    
      ...
      println(10.quoted("'"))
      // macro-expanded to `"'" + 10 + "'"`
      
      println(3 copiesOf new Entity)
      // macro-expanded to `Array.fill(3)(new Entity)`
      ```

  - *[Compilets](https://github.com/nativelibs4java/Scalaxy/tree/master/Obsolete/Compilets)* provide an easy way to express AST rewrites, backed by a compiler plugin and an sbt plugin.

  - *[Debug](https://github.com/nativelibs4java/Scalaxy/tree/master/Debug)* ([ScalaDoc](http://ochafik.github.io/Scalaxy/Debug/latest/api/index.html)) provides `assert`, `require` and `assume` macros that automatically add a useful message to the regular [Predef](http://www.scala-lang.org/api/current/index.html#scala.Predef$) calls.
    Please prefer [Assertions and DiagrammedAssertions](http://doc.scalatest.org/2.2.0/index.html#org.scalatest.DiagrammedAssertions) from ScalaTest.

# Discuss

If you have suggestions / questions:
- [@ochafik on Twitter](http://twitter.com/ochafik)
- [NativeLibs4Java mailing-list](groups.google.com/group/nativelibs4java)

You can also [file bugs and enhancement requests here](https://github.com/nativelibs4java/Scalaxy/issues/new).

Any help (testing, patches, bug reports) will be greatly appreciated!

# Hacking

- Pushing the site with each sub-project's Scaladoc at [http://ochafik.github.io/Scalaxy/](http://ochafik.github.io/Scalaxy/):

    ```
    sbt clean
    sbt "project scalaxy-doc" ghpages-push-site
    ```
  (you can preview the site with `sbt "project scalaxy-doc" preview-site`)

- Publishing projects on Sonatype OSS Repository + advertise on ls.implicit.ly (assuming correct credentials in `~/.sbt/0.13/sonatype.sbt`):

    ```
    sbt "+ assembly" "+ publish"
    sbt "project scalaxy" ls-write-version lsync
    ```

