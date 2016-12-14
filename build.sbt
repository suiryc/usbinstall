import sbt._
import Keys._

lazy val versions = Map[String, String](
  "akka"          -> "2.4.14",
  "config"        -> "1.3.1",
  "logback"       -> "1.1.8",
  "scala"         -> "2.12.1",
  "scala-logging" -> "3.5.0",
  "slf4j"         -> "1.7.21",
  "suiryc-scala"  -> "0.0.2-SNAPSHOT",
  "usbinstall"    -> "0.0.2-SNAPSHOT"
)


lazy val usbinstall = project.in(file(".")).
  settings(
    organization := "suiryc",
    name := "usbinstall",
    version := versions("usbinstall"),
    scalaVersion := versions("scala"),

    scalacOptions ++= Seq(
      "-deprecation",
      "-encoding", "UTF-8",
      "-feature",
      "-unchecked",
      "-Xfatal-warnings",
      "-Xlint",
      "-Yno-adapted-args",
      "-Ywarn-numeric-widen",
      "-Ywarn-value-discard",
      "-Ywarn-inaccessible",
      "-Ywarn-infer-any",
      "-Ywarn-dead-code",
      "-Ywarn-nullary-override",
      "-Ywarn-nullary-unit",
      "-Ywarn-unused",
      "-Ywarn-unused-import"
    ),
    scalacOptions in (Compile, doc) ++= Seq("-diagrams", "-implicits"),
    resolvers += Resolver.mavenLocal,

    libraryDependencies ++= Seq(
      "ch.qos.logback"             %  "logback-classic"     % versions("logback"),
      "com.typesafe"               %  "config"              % versions("config"),
      "com.typesafe.akka"          %% "akka-actor"          % versions("akka"),
      "com.typesafe.scala-logging" %% "scala-logging"       % versions("scala-logging"),
      "org.slf4j"                  %  "slf4j-api"           % versions("slf4j"),
      "suiryc"                     %% "suiryc-scala-core"   % versions("suiryc-scala"),
      "suiryc"                     %% "suiryc-scala-javafx" % versions("suiryc-scala"),
      "suiryc"                     %% "suiryc-scala-log"    % versions("suiryc-scala")
    ),

    publishMavenStyle := true,
    publishTo := Some(Resolver.mavenLocal)
  )
