import ReleaseTransformations._

val Scala212 = "2.12.15"
val Scala213 = "2.13.8"
val Scala30 = "3.0.2"
val Scala31 = "3.1.0"

ThisBuild / crossScalaVersions := Seq(Scala31, Scala30, Scala212, Scala213)
ThisBuild / scalaVersion := (ThisBuild / crossScalaVersions).value.last

/*
val scalaMajorVersion = SettingKey[Int]("scalaMajorVersion")

lazy val scalaVersionSettings = Seq(
  scalaVersion := "2.12.8",
  crossScalaVersions := Seq("2.13.8", scalaVersion.value),
  scalaMajorVersion := {
    val v = scalaVersion.value
    CrossVersion.partialVersion(v).map(_._2.toInt).getOrElse {
      throw new RuntimeException(s"could not get Scala major version from $v")
    }
  }
)
*/

// scalaVersion := "2.12.8"

name := "simple-cassandra-client-with-cats-scala"

// https://github.com/typelevel/scalacheck/pull/411/files
/*
lazy val compilerOptions = Seq(
  // "-Xfatal-warnings",
  "-deprecation",
  "-feature",
  "-unchecked",
  "-language:_"
) ++ {
  val modern = Seq("-Xlint:-unused", "-Ywarn-infer-any", "-Ywarn-unused-import", "-Ywarn-unused:-patvars,-implicits,-locals,-privates,-explicits")
  scalaMajorVersion.value match {
    case 10 => Seq("-Xfatal-warnings", "-Xlint")
    case 11 => Seq("-Xfatal-warnings", "-Xlint", "-Ywarn-infer-any", "-Ywarn-unused-import")
    case 12 => "-Xfatal-warnings" +: modern
    case 13 => modern
  }
}
*/

lazy val commonSettings = Seq(
  organization := "com.eztier",
  version := "0.1.4",
  
  /*
  credentials ++= (for {
    username <- Option(System.getenv().get("SONATYPE_USERNAME"))
    password <- Option(System.getenv().get("SONATYPE_PASSWORD"))
  } yield Credentials(
    "Sonatype Nexus Repository Manager",
    "oss.sonatype.org",
    username, password
  )).toSeq,
  */

  Compile / unmanagedSourceDirectories in Compile += (baseDirectory in LocalRootProject).value / "src" / "main" / "scala",

  Compile / unmanagedSourceDirectories += {
    val s = CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((3,  _)) => "scala-2.13+"
      case Some((2, 13)) => "scala-2.13+"
      case _             => "scala-2.13-"
    }
    (LocalRootProject / baseDirectory).value / "src" / "main" / s
  },

  Test / unmanagedSourceDirectories in Test += (baseDirectory in LocalRootProject).value / "src" / "test" / "scala",

  // scalaVersion := "2.12.8",

  scalaVersion := Scala213,
  crossScalaVersions := Seq(Scala212, Scala213),

  // 2.11 - 2.13
  scalacOptions ++= {
    def mk(r: Range)(strs: String*): Int => Seq[String] =
      (n: Int) => if (r.contains(n)) strs else Seq.empty

    // Remove "-Ywarn-unused-import" for 12.  
    val groups: Seq[Int => Seq[String]] = Seq(
      mk(12 to 12)("-language:_", "-Ywarn-inaccessible", "-Ywarn-nullary-override",
        "-Ywarn-nullary-unit", "-Xfuture", "-Xfatal-warnings", "-deprecation",
        "-Ywarn-infer-any"),
      mk(12 to 13)("-language:_", "-encoding", "UTF-8", "-feature", "-unchecked",
        "-Ywarn-dead-code", "-Ywarn-numeric-widen", "-Xlint:-unused",
        "-Ywarn-unused:-patvars,-implicits,-locals,-privates,-explicits"))

    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, n)) => groups.flatMap(f => f(n.toInt))
      case _            => Seq("-language:Scala2")
    }
  },

  // HACK: without these lines, the console is basically unusable,
  // since all imports are reported as being unused (and then become
  // fatal errors).
  scalacOptions in (Compile, console) ~= {_.filterNot("-Ywarn-unused-import" == _)},
  scalacOptions in (Test, console) := (scalacOptions in (Compile, console)).value,

  // don't use fatal warnings in tests
  scalacOptions in Test ~= (_ filterNot (_ == "-Xfatal-warnings")),

  addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1"),
  // addCompilerPlugin("org.typelevel" %% "kind-projector" % "0.11.0" cross CrossVersion.full),
  addCompilerPlugin("org.typelevel" % "kind-projector" % "0.13.2" cross CrossVersion.full),
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
val LogbackVersion = "1.2.3"

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
// val scalaCass = "com.github.thurstonsand" %% "scala-cass" % ScalaCassVersion
val logback = "ch.qos.logback" % "logback-classic" % LogbackVersion

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
      // scalaCass,
      specs2,
      logback
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
  // checkSnapshotDependencies,
  inquireVersions,
  // runClean,
  // runTest,
  setReleaseVersion,
  commitReleaseVersion,
  tagRelease,
  // releaseStepCommand("publishSigned"),
  // publishArtifacts,
  setNextVersion,
  commitNextVersion,
  // releaseStepCommand("sonatypeRelease"),
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
