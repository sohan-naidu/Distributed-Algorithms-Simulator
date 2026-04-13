import scala.collection.Seq

ThisBuild / scalaVersion := "3.3.4"

lazy val commonDependencies = Seq(
  "org.slf4j" % "slf4j-api" % "2.0.13",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.9.5",
  "ch.qos.logback" % "logback-classic" % "1.5.18",
  "org.scalameta" %% "munit" % "0.7.29" % Test,
  "org.scalameta" %% "munit-scalacheck" % "0.7.29" % Test,
  "io.circe" %% "circe-core"    % "0.14.6",
  "io.circe" %% "circe-generic" % "0.14.6",
  "io.circe" %% "circe-parser"  % "0.14.6",
  "com.typesafe" % "config" % "1.4.3",
)

lazy val cli = (project in file("cli"))
  .dependsOn(core, enricher)
  .settings(
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

lazy val base = (project in file("core/translator/base"))
  .settings(
    scalaVersion := "3.3.4",
    libraryDependencies := Seq.empty
  )

lazy val translator = (project in file("core/translator"))
  .dependsOn(core, enricher, base)
  .settings(
    libraryDependencies ++= commonDependencies ++ Seq(
      "com.typesafe.akka" %% "akka-actor-typed" % "2.10.7",
      "com.typesafe.akka" %% "akka-stream"      % "2.10.7"
    ),
    excludeDependencies ++= Seq(
      ExclusionRule("com.typesafe.akka", "akka-actor-typed_2.13"),
      ExclusionRule("com.typesafe.akka", "akka-actor_2.13"),
      ExclusionRule("com.typesafe.akka", "akka-stream_2.13"),
      ExclusionRule("com.typesafe.akka", "akka-protobuf-v3_2.13"),
      ExclusionRule("com.typesafe.akka", "akka-slf4j_2.13")
    )
  )

lazy val root = (project in file("."))
  .aggregate(cli, core, enricher, translator)
  .settings(
    Compile / mainClass := (cli / Compile / mainClass).value
  )


