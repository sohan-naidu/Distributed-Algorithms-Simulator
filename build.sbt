import scala.collection.Seq

ThisBuild / scalaVersion := "3.2.2"

lazy val commonDependencies = Seq(
  "ch.qos.logback" % "logback-classic" % "1.2.10",
).map(_.exclude("org.slf4j", "*"))

lazy val cli = (project in file("cli"))
  .settings(
    scalaVersion := "3.2.2",
    name := "simulator",
    libraryDependencies ++= commonDependencies ++ Seq("com.monovore" %% "decline" % "2.4.1")
  )

lazy val root = (project in file("."))
  .aggregate(cli)
  .settings(
    Compile / mainClass := (cli / Compile / mainClass).value
  )


