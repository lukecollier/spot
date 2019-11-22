ThisBuild / scalaVersion := "2.13.1"

ThisBuild / organization := "org.lukecollier"

lazy val catsVersion = "2.0.0"
lazy val xmlVersion = "1.2.0"
lazy val circeVersion = "0.11.1"
lazy val sttpVersion = "2.0.0-M7"
lazy val utestVersion = "0.7.1" 

lazy val spot = (project in file("."))
  .settings(
    name := "Spot",

    libraryDependencies ++= Seq(
      "com.lihaoyi" %% "utest" % utestVersion % Test,
      "org.typelevel" %% "cats-core" % catsVersion,
      "org.typelevel" %% "cats-effect" % catsVersion,
      "org.typelevel" %% "kittens" % catsVersion,
      "com.softwaremill.sttp.client" %% "core" % sttpVersion,
      "com.softwaremill.sttp.client" %% "circe" % sttpVersion,
      "com.softwaremill.sttp.client" %% "async-http-client-backend-cats" % sttpVersion,
      "org.scala-lang.modules" %% "scala-xml" % xmlVersion
    ),

    libraryDependencies ++= Seq(
      "io.chrisdavenport" %% "log4cats-core" % "1.0.1",
      "io.chrisdavenport" %% "log4cats-slf4j" % "1.0.1",
      "org.rogach" %% "scallop" % "3.3.1",
      "co.fs2" %% "fs2-core" % "2.0.0", // for cats 1.5.0 and cats-effect 1.2.0
      "co.fs2" %% "fs2-io" % "2.0.0", // for cats 1.5.0 and cats-effect 1.2.0
      "com.ovoenergy" %% "fs2-kafka" % "0.20.2",
      "ch.qos.logback" % "logback-classic" % "1.2.3"
    ),

    testFrameworks += TestFramework("utest.runner.Framework"),

    scalacOptions ++= Seq("-Xfatal-warnings", "-deprecation", "-language:higherKinds")
  )


