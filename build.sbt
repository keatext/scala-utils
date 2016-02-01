name := "mintosci-scala-utils"

organization := "com.keatext"

version := version.value

scalaVersion := "2.11.7"

scalacOptions := Seq(
  "-unchecked", "-deprecation", "-encoding", "utf8", "-feature", "-Xlint", "-Xfatal-warnings"
)

libraryDependencies ++= {
  val version = Map(
    "akka"         -> "2.3.9",
    "akka-stream"  -> "2.0-M2",
    "postgresql"   -> "9.4-1201-jdbc41",
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
    "org.slf4j"             % "slf4j-nop"                         % version("slf4j")
  )
}

// uncomment to display inferred types and implicits upon recompilation, useful for debugging
//scalacOptions in Compile ++= Seq("-Xprint-types", "-Xprint:typer")