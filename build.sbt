name := "scala-utils"

organization := "com.keatext"

version := version.value

scalaVersion := "2.11.8"

scalacOptions := Seq(
  "-unchecked", "-deprecation", "-encoding", "utf8", "-feature", "-Xlint", "-Xfatal-warnings"
)

libraryDependencies ++= {
  val version = Map(
    "akka"         -> "2.4.6",
    "postgresql"   -> "9.4-1201-jdbc41",
    "scala-test"   -> "2.2.1",
    "slf4j"        -> "1.6.4",
    "slick"        -> "3.0.3",
    "spray"        -> "1.3.2"
  )

  Seq(
    "com.typesafe.akka"    %% "akka-http-core"                    % version("akka"),
    "com.typesafe.akka"    %% "akka-http-experimental"            % version("akka"),
    "com.typesafe.akka"    %% "akka-http-spray-json-experimental" % version("akka"),
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
  val nexus = "https://nexus.keatext.ai/repository/"
  if (isSnapshot.value)
    Some("snapshots" at nexus + "snapshots")
  else
    Some("releases"  at nexus + "releases")
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
