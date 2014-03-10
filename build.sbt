organization := "suiryc"

name := "usbinstall"

version := "0.0.1-SNAPSHOT"

scalaVersion := "2.10.3"

scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature", "-optimize")

scalacOptions in (Compile, doc) ++= Seq("-diagrams", "-implicits")

resolvers += "Local Maven Repository" at "file://" + Path.userHome.absolutePath + "/.m2/repository"

seq(Revolver.settings: _*)

libraryDependencies ++= Seq(
  "org.slf4j" % "slf4j-api" % "1.7.5",
  "ch.qos.logback" % "logback-classic" % "1.0.13" % "runtime",
  "com.typesafe" % "config" % "1.0.2",
  "com.typesafe.akka" %% "akka-actor" % "2.2.3",
  "org.scalafx" %% "scalafx" % "8.0.0-M3",
  "org.scalafx" %% "scalafxml-core" % "0.1",
  "org.controlsfx" % "controlsfx" % "8.0.4",
  "suiryc" %% "suiryc-scala-core" % "0.0.1-SNAPSHOT",
  "suiryc" %% "suiryc-scala-log" % "0.0.1-SNAPSHOT",
  "suiryc" %% "suiryc-scala-javafx" % "0.0.1-SNAPSHOT"
)

addCompilerPlugin("org.scalamacros" % "paradise" % "2.0.0-M3" cross CrossVersion.full)
