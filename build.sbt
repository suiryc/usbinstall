import sbt._
import Keys._

val versions = Map[String, String](
  "scala"        -> "2.11.7",
  "akka"         -> "2.4.1",
  "config"       -> "1.3.0",
  "grizzled"     -> "1.0.2",
  "logback"      -> "1.1.3",
  "slf4j"        -> "1.7.13",
  "suiryc-scala" -> "0.0.2-SNAPSHOT",
  "usbinstall"   -> "0.0.2-SNAPSHOT"
)


lazy val usbinstall = project.in(file(".")).
  settings(
    organization := "suiryc",
    name := "usbinstall",
    version := versions("usbinstall"),
    scalaVersion := versions("scala"),

    scalacOptions ++= Seq(
      "-deprecation",
      "-feature",
      "-optimize",
      "-unchecked",
      "-Yinline-warnings"
    ),
    scalacOptions in (Compile, doc) ++= Seq("-diagrams", "-implicits"),
    resolvers += Resolver.mavenLocal,

    libraryDependencies ++= Seq(
      "ch.qos.logback"    %  "logback-classic"     % versions("logback"),
      "com.typesafe"      %  "config"              % versions("config"),
      "com.typesafe.akka" %% "akka-actor"          % versions("akka"),
      "org.clapper"       %% "grizzled-slf4j"      % versions("grizzled"),
      "org.slf4j"         %  "slf4j-api"           % versions("slf4j"),
      "suiryc"            %% "suiryc-scala-core"   % versions("suiryc-scala"),
      "suiryc"            %% "suiryc-scala-javafx" % versions("suiryc-scala"),
      "suiryc"            %% "suiryc-scala-log"    % versions("suiryc-scala")
    ),

    publishMavenStyle := true,
    publishTo := Some(Resolver.mavenLocal)
  )
