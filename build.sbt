organization := "suiryc"

name := "usbinstall"

version := "0.0.2-SNAPSHOT"

scalaVersion := "2.11.1"

scalacOptions ++= Seq("-deprecation", "-feature", "-optimize", "-unchecked", "-Yinline-warnings")

scalacOptions in (Compile, doc) ++= Seq("-diagrams", "-implicits")

fork in run := true

val versions = Map[String, String](
  "java" -> "1.8",
  "akka" -> "2.3.3",
  "config" -> "1.2.1",
  "controlsfx" -> "8.0.6",
  "grizzled" -> "1.0.2",
  "logback" -> "1.1.2",
  "paradise" -> "2.0.1",
  "scalafx" -> "8.0.5-R5",
  "scalafxml" -> "0.2.1-SNAPSHOT",
  "slf4j" -> "1.7.7",
  "suiryc-scala" -> "0.0.2-SNAPSHOT",
  "maven-compiler-plugin" -> "3.1",
  "maven-surefire-plugin" -> "2.17",
  "scala-maven-plugin" -> "3.1.6"
)

libraryDependencies ++= Seq(
  "org.slf4j" % "slf4j-api" % versions("slf4j"),
  "org.clapper" %% "grizzled-slf4j" % versions("grizzled"),
  "ch.qos.logback" % "logback-classic" % versions("logback"),
  "com.typesafe" % "config" % versions("config"),
  "com.typesafe.akka" %% "akka-actor" % versions("akka"),
  "org.scalafx" %% "scalafx" % versions("scalafx"),
  "org.scalafx" %% "scalafxml-core" % versions("scalafxml"),
  "org.controlsfx" % "controlsfx" % versions("controlsfx"),
  "suiryc" %% "suiryc-scala-core" % versions("suiryc-scala"),
  "suiryc" %% "suiryc-scala-log" % versions("suiryc-scala"),
  "suiryc" %% "suiryc-scala-javafx" % versions("suiryc-scala")
)

val localMavenPath = Path.userHome.absolutePath + "/.m2/repository"

resolvers += "Local Maven Repository" at "file://" + localMavenPath

seq(Revolver.settings: _*)

addCompilerPlugin("org.scalamacros" % "paradise" % versions("paradise") cross CrossVersion.full)


publishMavenStyle := true

publishTo := Some(Resolver.file("file",  new File(localMavenPath)))

pomExtra := (
  <properties>
    <encoding>UTF-8</encoding>
  </properties>
  <build>
    <sourceDirectory>src/main/scala</sourceDirectory>
    <testSourceDirectory>src/test/scala</testSourceDirectory>
    <plugins>
      <plugin>
        <groupId>net.alchim31.maven</groupId>
        <artifactId>scala-maven-plugin</artifactId>
        <version>{ versions("scala-maven-plugin") }</version>
        <configuration>
          <args>
            <arg>-deprecation</arg>
            <arg>-feature</arg>
            <arg>-Yinline-warnings</arg>
            <arg>-optimize</arg>
            <arg>-unchecked</arg>
          </args>
          <compilerPlugins>
            <compilerPlugin>
              <groupId>org.scalamacros</groupId>
              <artifactId>paradise_2.10.3</artifactId>
              <version>{ versions("paradise") }</version>
            </compilerPlugin>
          </compilerPlugins>
        </configuration>
        <executions>
          <execution>
            <goals>
              <goal>compile</goal>
              <goal>testCompile</goal>
            </goals>
          </execution>
        </executions>
      </plugin>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-compiler-plugin</artifactId>
        <version>{ versions("maven-compiler-plugin") }</version>
        <configuration>
          <source>{ versions("java") }</source>
          <target>{ versions("java") }</target>
        </configuration>
      </plugin>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-surefire-plugin</artifactId>
        <version>{ versions("maven-surefire-plugin") }</version>
        <configuration>
          <includes>
            <include>**/*Suite.class</include>
          </includes>
        </configuration>
      </plugin>
    </plugins>
  </build>
)
