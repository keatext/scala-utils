name := "mintosci-scala-utils"

organization := "com.keatext"

version := version.value

scalaVersion := "2.11.7"

scalacOptions := Seq(
  "-unchecked", "-deprecation", "-encoding", "utf8", "-feature", "-Xlint", "-Xfatal-warnings"
)

// uncomment to display inferred types and implicits upon recompilation, useful for debugging
//scalacOptions in Compile ++= Seq("-Xprint-types", "-Xprint:typer")
