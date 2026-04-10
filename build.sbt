import scala.collection.Seq

ThisBuild / scalaVersion := "3.2.2"

lazy val commonDependencies = Seq(
  "org.slf4j" % "slf4j-api" % "2.0.13",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.9.5",
  "ch.qos.logback" % "logback-classic" % "1.5.18",
  "org.scalameta" %% "munit" % "0.7.29" % Test,
  "org.scalameta" %% "munit-scalacheck" % "0.7.29" % Test,
  "io.circe" %% "circe-core"    % "0.14.6",
  "io.circe" %% "circe-generic" % "0.14.6",
  "io.circe" %% "circe-parser"  % "0.14.6"
)

lazy val cli = (project in file("cli"))
  .dependsOn(core, enricher)
  .settings(
    scalaVersion := "3.2.2",
    name := "simulator",
    libraryDependencies ++= commonDependencies ++ Seq("com.monovore" %% "decline" % "2.4.1")
  )

lazy val core = (project in file("core/"))
  .settings(
    libraryDependencies ++= commonDependencies
  )
lazy val enricher = (project in file("core/enricher"))
  .dependsOn(core)
  .settings(
    libraryDependencies ++= commonDependencies,
    Test / classLoaderLayeringStrategy := ClassLoaderLayeringStrategy.Flat,
  )

lazy val root = (project in file("."))
  .aggregate(cli, core, enricher)
  .settings(
    Compile / mainClass := (cli / Compile / mainClass).value
  )


