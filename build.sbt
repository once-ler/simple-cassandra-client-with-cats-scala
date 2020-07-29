import ReleaseTransformations._

scalaVersion := "2.12.8"

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
  addCompilerPlugin("org.typelevel" %% "kind-projector" % "0.11.0" cross CrossVersion.full),
  resolvers ++= Seq(
    Resolver.sonatypeRepo("public"),
    Resolver.sonatypeRepo("releases"),
    Resolver.sonatypeRepo("snapshots"),
    Resolver.bintrayIvyRepo("thurstonsand", "maven"),
    Resolver.jcenterRepo
  )
)

lazy val settings = commonSettings

val CatsVersion = "2.0.0"
val FS2Version = "2.0.0"
val CassandraCoreVersion = "3.8.0"
val GuavaVersion = "19.0"
val CirceConfigVersion = "0.7.0"
val Specs2Version = "4.10.0"
val ScalaCassVersion = "3.2.1-3.5.0"

val cats = "org.typelevel" %% "cats-core" % CatsVersion
val fs2 = "co.fs2" %% "fs2-core" % FS2Version
val cassandraCore = "com.datastax.cassandra" % "cassandra-driver-core" % CassandraCoreVersion
val guava = "com.google.guava" % "guava" % GuavaVersion
val CirceVersion = "0.12.1"
val CirceGenericExVersion = "0.12.2"
val circe = "io.circe" %% "circe-core" % CirceVersion
val circeGenericExtras = "io.circe" %% "circe-generic-extras" % CirceGenericExVersion
val circeConfig = "io.circe" %% "circe-config" % CirceConfigVersion
val specs2 = "org.specs2" %% "specs2-core" % Specs2Version % "test"
val scalaCass = "com.github.thurstonsand" %% "scala-cass" % ScalaCassVersion

lazy val simpleCassandraClientWithCats = project
  .in(file("."))
  .settings(
    name := "common",
    settings,
    assemblySettings,
    libraryDependencies ++= Seq(
      scalaOrganization.value %  "scala-reflect" % scalaVersion.value, // required for shapeless macros
      cats,
      fs2,
      cassandraCore,
      circe,
      circeGenericExtras,
      circeConfig,
      scalaCass,
      specs2
    ),
    dependencyOverrides ++= Seq(
      guava
    )
  )

// Publishing
sonatypeProfileName := "com.eztier"

licenses := Seq("The Apache Software License, Version 2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0.txt"))

homepage := Some(url("https://github.com/once-ler/simple-cassandra-client-with-cats-scala"))

publishMavenStyle := true

publishArtifact in Test := false

pomIncludeRepository := {_ => false}

releaseCrossBuild := false

releasePublishArtifactsAction := PgpKeys.publishSigned.value

// publishTo := Some(Resolver.file("file", new File("/home/htao/tmp")))

publishTo := sonatypePublishTo.value

/*
publishTo := Some(
  if (isSnapshot.value)
    "snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"
  else
    "releases" at "https://oss.sonatype.org/service/local/staging/deploy/maven2")
*/

scmInfo := Some(
  ScmInfo(
    browseUrl = url("https://github.com/once-ler/simple-cassandra-client-with-cats-scala"),
    connection = "scm:git@github.com:once-ler/simple-cassandra-client-with-cats-scala.git"
  )
)

developers := List(
  Developer(
    id = "once-ler",
    name = "Henry Tao",
    email = "htao@eztier.com",
    url = url("https://github.com/once-ler")
  )
)

releaseProcess := Seq[ReleaseStep](
  checkSnapshotDependencies,
  inquireVersions,
  runClean,
  runTest,
  setReleaseVersion,
  // commitReleaseVersion,
  tagRelease,
  // releaseStepCommand("publishSigned"),
  // publishArtifacts,
  setNextVersion,
  commitNextVersion,
  releaseStepCommand("sonatypeRelease"),
  pushChanges
)

// sbt-assembly
// Filter out compiler flags to make the repl experience functional...
val badConsoleFlags = Seq("-Xfatal-warnings", "-Ywarn-unused:imports")
scalacOptions in (Compile, console) ~= (_.filterNot(badConsoleFlags.contains(_)))

lazy val assemblySettings = Seq(
  assemblyJarName in assembly := s"${name.value}-${version.value}.jar",

  assemblyMergeStrategy in assembly := {
    case x =>
      val oldStrategy = (assemblyMergeStrategy in assembly).value
      val strategy = oldStrategy(x)
      if (strategy == MergeStrategy.deduplicate)
        MergeStrategy.first
      else
        strategy
  },
  test in assembly := {}
)
