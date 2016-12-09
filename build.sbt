import sbt._
import Keys._
import sbtassembly.AssemblyPlugin.autoImport._


// Project name (artifact name in Maven)
name := "partitions"

// organization name (e.g., the package name of the project)
organization := "com.vesperin"

version := "0.1"

// project description
description := "Partitions projects into clusters based on shared words"

// Enables publishing to maven repo
publishMavenStyle := true

// Do not append Scala versions to the generated artifacts
crossPaths := false

scalaVersion := "2.12.0"

// This forbids including Scala related libraries into the dependency
autoScalaLibrary := false

assemblyMergeStrategy in assembly := {
  case PathList("META-INF", xs @ _*) => MergeStrategy.discard
  case PathList("about_files", xs @ _*) => MergeStrategy.discard
  case x => MergeStrategy.first
}


assemblyJarName in assembly:= "partitions.jar"

test in assembly := {}


// library dependencies. (organization name) % (project name) % (version)
libraryDependencies ++= Seq(
    "com.google.code.gson" % "gson" % "2.7",
    "com.github.rvesse" % "airline" % "2.1.0"

)

assemblyExcludedJars in assembly <<= (fullClasspath in assembly) map { cp =>
  val excludes = Set(
    "junit-4.12.jar",
    "hamcrest-core-1.3.jar",
    "junit-interface-0.11.jar"
  )

  cp filter { jar => excludes(jar.data.getName) }
}
