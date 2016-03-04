name := "scala-utils"

organization := "com.keatext"

version := version.value

scalaVersion := "2.11.7"

scalacOptions := Seq(
  "-unchecked", "-deprecation", "-encoding", "utf8", "-feature", "-Xlint", "-Xfatal-warnings"
)

libraryDependencies ++= {
  val version = Map(
    "akka"         -> "2.3.12",
    "akka-stream"  -> "2.0-M2",
    "postgresql"   -> "9.4-1201-jdbc41",
    "scala-test"   -> "2.2.1",
    "slf4j"        -> "1.6.4",
    "slick"        -> "3.0.3",
    "spray"        -> "1.3.2"
  )

  Seq(
    "com.typesafe.akka"    %% "akka-actor"                        % version("akka"),
    "com.typesafe.akka"    %% "akka-http-core-experimental"       % version("akka-stream"),
    "com.typesafe.akka"    %% "akka-http-experimental"            % version("akka-stream"),
    "com.typesafe.akka"    %% "akka-http-spray-json-experimental" % version("akka-stream"),
    "com.typesafe.akka"    %% "akka-stream-experimental"          % version("akka-stream"),
    "com.typesafe.slick"   %% "slick"                             % version("slick"),
    "io.spray"             %% "spray-json"                        % version("spray"),
    "org.postgresql"        % "postgresql"                        % version("postgresql"),
    "org.scalatest"        %% "scalatest"                         % version("scala-test") % "test",
    "org.slf4j"             % "slf4j-nop"                         % version("slf4j")
  )
}

// uncomment to display inferred types and implicits upon recompilation, useful for debugging
//scalacOptions in Compile ++= Seq("-Xprint-types", "-Xprint:typer")


import ReleaseTransformations._

// Change the version bump for the next version
// Available are Bugfix, Minor, Major.
releaseVersionBump := sbtrelease.Version.Bump.Bugfix

credentials += Credentials(Path.userHome / ".ivy2" / ".credentials")

publishTo in Global := {
  val nexus = "http://data/"
  if (isSnapshot.value)
    Some("snapshots" at nexus + "nexus/content/repositories/snapshots")
  else
    Some("releases"  at nexus + "nexus/content/repositories/releases")
}

releaseProcess := Seq[ReleaseStep](
  inquireVersions,
  setReleaseVersion,
  commitReleaseVersion,
  tagRelease,
  publishArtifacts,
  setNextVersion,
  commitNextVersion,
  pushChanges
)
