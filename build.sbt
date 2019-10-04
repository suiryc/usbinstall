import sbt._
import Keys._

lazy val versions = Map[String, String](
  "akka"          -> "2.5.25",
  "config"        -> "1.3.4",
  "javafx"        -> "12.0.1",
  "logback"       -> "1.2.3",
  "monix"         -> "3.0.0",
  "scala"         -> "2.13.1",
  "scala-logging" -> "3.9.2",
  "slf4j"         -> "1.7.28",
  "suiryc-scala"  -> "0.0.4-SNAPSHOT",
  "usbinstall"    -> "0.0.3-SNAPSHOT"
)


lazy val usbinstall = project.in(file(".")).
  settings(
    organization := "suiryc",
    name := "usbinstall",
    version := versions("usbinstall"),
    scalaVersion := versions("scala"),

    scalacOptions ++= Seq(
      "-encoding", "UTF-8",
      "-feature",
      "-unchecked",
      "-Werror",
      "-Xlint:_",
      "-Ywarn-dead-code",
      "-Ywarn-numeric-widen",
      "-Ywarn-unused:_",
      "-Ywarn-value-discard"
    ),
    resolvers += Resolver.mavenLocal,

    libraryDependencies ++= Seq(
      "ch.qos.logback"             %  "logback-classic"     % versions("logback"),
      "com.typesafe"               %  "config"              % versions("config"),
      "com.typesafe.akka"          %% "akka-actor"          % versions("akka"),
      "com.typesafe.akka"          %% "akka-slf4j"          % versions("akka"),
      "com.typesafe.scala-logging" %% "scala-logging"       % versions("scala-logging"),
      "io.monix"                   %% "monix"               % versions("monix"),
      "org.openjfx"                %  "javafx-base"         % versions("javafx") classifier jfxPlatform,
      "org.openjfx"                %  "javafx-controls"     % versions("javafx") classifier jfxPlatform,
      "org.openjfx"                %  "javafx-fxml"         % versions("javafx") classifier jfxPlatform,
      "org.openjfx"                %  "javafx-graphics"     % versions("javafx") classifier jfxPlatform,
      "org.slf4j"                  %  "slf4j-api"           % versions("slf4j"),
      "suiryc"                     %% "suiryc-scala-core"   % versions("suiryc-scala"),
      "suiryc"                     %% "suiryc-scala-javafx" % versions("suiryc-scala"),
      "suiryc"                     %% "suiryc-scala-log"    % versions("suiryc-scala")
    ),

    publishMavenStyle := true,
    publishTo := Some(Resolver.mavenLocal)
  )

assemblyMergeStrategy in assembly := {
  case "module-info.class" => MergeStrategy.discard
  case x =>
    val oldStrategy = (assemblyMergeStrategy in assembly).value
    oldStrategy(x)
}

lazy val jfxPlatform = {
  val osName = System.getProperty("os.name", "").toLowerCase
  if (osName.startsWith("mac")) "mac"
  else if (osName.startsWith("win")) "win"
  else "linux"
}
