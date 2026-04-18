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
  "com.github.pureconfig" %% "pureconfig-core" % "0.17.10",
  "com.github.pureconfig" %% "pureconfig-generic-scala3" % "0.17.10"
)

lazy val cli = (project in file("cli"))
  .enablePlugins(Cinnamon)
  .dependsOn(core, enricher, translator)
  .settings(
    run / fork := true,
    run / baseDirectory := (ThisBuild / baseDirectory).value,
    run / javaOptions += s"-Duser.dir=${(ThisBuild / baseDirectory).value}",
    run / cinnamon := true,
    test / cinnamon := true,
    cinnamonLogLevel := "INFO",
    libraryDependencies ++= commonDependencies ++ Seq(
      "com.monovore" %% "decline" % "2.4.1",
      Cinnamon.library.cinnamonAkka,
      Cinnamon.library.cinnamonAkkaTyped,
      Cinnamon.library.cinnamonCHMetrics
    )
  )

lazy val core = (project in file("core/"))
  .settings(
    libraryDependencies ++= commonDependencies
  )
lazy val enricher = (project in file("core/enricher"))
  .dependsOn(core)
  .settings(
    libraryDependencies ++= commonDependencies ++ Seq(
      "guru.nidi" % "graphviz-java" % "0.18.1"
    ),
    Test / classLoaderLayeringStrategy := ClassLoaderLayeringStrategy.Flat,
  )

lazy val framework = (project in file("core/framework"))

lazy val algorithms = (project in file("core/algorithms"))
  .dependsOn(enricher, framework, core)

lazy val translator = (project in file("core/translator"))
  .dependsOn(core, enricher, framework, algorithms)
  .settings(
    libraryDependencies ++= commonDependencies ++ Seq(
      "com.typesafe.akka" %% "akka-actor-typed" % "2.10.7",
      "com.typesafe.akka" %% "akka-stream"      % "2.10.7"
    ),
  )

lazy val root = (project in file("."))
  .aggregate(cli, core, enricher, translator, algorithms)
  .settings(
    Compile / mainClass := (cli / Compile / mainClass).value
  )
