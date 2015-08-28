organization := "suiryc"

name := "usbinstall"

version := "0.0.2-SNAPSHOT"

val versions = Map[String, String](
  "java" -> "1.8",
  "scala" -> "2.11.1",
  "akka" -> "2.3.3",
  "config" -> "1.2.1",
  "controlsfx" -> "8.40.9",
  "grizzled" -> "1.0.2",
  "logback" -> "1.1.2",
  "slf4j" -> "1.7.7",
  "suiryc-scala" -> "0.0.2-SNAPSHOT",
  "maven-compiler-plugin" -> "3.1",
  "maven-surefire-plugin" -> "2.17",
  "scala-maven-plugin" -> "3.1.6"
)

scalaVersion := versions("scala")

scalacOptions ++= Seq("-deprecation", "-feature", "-optimize", "-unchecked", "-Yinline-warnings")

scalacOptions in (Compile, doc) ++= Seq("-diagrams", "-implicits")

fork in run := true

libraryDependencies ++= Seq(
  "org.slf4j" % "slf4j-api" % versions("slf4j"),
  "org.clapper" %% "grizzled-slf4j" % versions("grizzled"),
  "ch.qos.logback" % "logback-classic" % versions("logback"),
  "com.typesafe" % "config" % versions("config"),
  "com.typesafe.akka" %% "akka-actor" % versions("akka"),
  "org.controlsfx" % "controlsfx" % versions("controlsfx"),
  "suiryc" %% "suiryc-scala-core" % versions("suiryc-scala"),
  "suiryc" %% "suiryc-scala-log" % versions("suiryc-scala"),
  "suiryc" %% "suiryc-scala-javafx" % versions("suiryc-scala")
)

resolvers += Resolver.mavenLocal

Seq(Revolver.settings: _*)


publishMavenStyle := true

publishTo := Some(Resolver.mavenLocal)

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
