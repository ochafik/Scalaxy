scalaVersion := "2.11.6"

// Add JavaFX Runtime as an unmanaged dependency, hoping to find it in the JRE's library folder.
unmanagedJars in Compile ++= Seq(new File(System.getProperty("java.home")) / "lib" / "jfxrt.jar")

libraryDependencies <+= scalaVersion("org.scala-lang" % "scala-compiler" % _)

libraryDependencies <+= scalaVersion("org.scala-lang" % "scala-reflect" % _)

// This is the bulk of Scalaxy/Fx, needed only during compilation (no runtime dependency here).
libraryDependencies += "com.nativelibs4java" %% "scalaxy-fx" % "0.4-SNAPSHOT" % "provided"

libraryDependencies += "junit" % "junit" % "4.11" % "test"

libraryDependencies += "com.novocode" % "junit-interface" % "0.11" % "test"

// JavaFX doesn't cleanup everything well, need to fork tests / runs.
fork := true

// Scalaxy snapshots are published on the Sonatype repository.
resolvers += Resolver.sonatypeRepo("snapshots")

resolvers += Resolver.defaultLocal
