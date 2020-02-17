libraryDependencies in ThisBuild += compilerPlugin(kindProjectorPlugin)

name := "simple-cassandra-client-with-cats-scala"

lazy val compilerOptions = Seq(
  "-Xfatal-warnings",
  "-deprecation",
  "-feature",
  "-unchecked",
  "-language:_"
)

lazy val commonSettings = Seq(
  organization := "com.eztier",
  version := "0.1.1-SNAPSHOT",
  scalaVersion := "2.12.8",
  scalacOptions ++= compilerOptions,
  addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1"),
  resolvers ++= Seq(
    Resolver.sonatypeRepo("public"),
    Resolver.sonatypeRepo("releases"),
    Resolver.sonatypeRepo("snapshots"),
    Resolver.bintrayRepo("ovotech", "maven")
  )
)

lazy val settings = commonSettings
