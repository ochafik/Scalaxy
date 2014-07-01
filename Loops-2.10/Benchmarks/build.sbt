organization := "com.nativelibs4java"

name := "scalaxy-loops-2.10-test"

version := "1.0-SNAPSHOT"

// Only works with 2.10.0+
scalaVersion := "2.10.4"

// Dependency at compilation-time only (not at runtime).
// libraryDependencies += "com.nativelibs4java" %% "scalaxy-loops" % "0.1" % "provided"
libraryDependencies += "com.nativelibs4java" %% "scalaxy-loops" % "0.3-SNAPSHOT" % "provided"

// Run benchmarks in cold VMs.
fork := true

// The latest release might not be synced to Maven Central yet:
// resolvers += Resolver.sonatypeRepo("releases")

// Scalaxy snapshots are published on the Sonatype repository.
resolvers += Resolver.sonatypeRepo("snapshots")

scalacOptions ++= Seq("-optimise", "-Yinline", "-Yclosure-elim", "-feature", "-deprecation")
