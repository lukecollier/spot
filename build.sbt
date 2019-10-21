ThisBuild / scalaVersion := "2.13.1"

ThisBuild / organization := "org.lukecollier"

lazy val catsVersion = "2.0.0"
lazy val xmlVersion = "1.2.0"
lazy val circeVersion = "0.11.1"
lazy val sttpVersion = "2.0.0-M7"

lazy val spot = (project in file("."))
  .settings(
    name := "Spot",

    libraryDependencies ++= Seq(
      "com.lihaoyi" %% "utest" % "0.7.1" % Test,
      "org.typelevel" %% "cats-core" % catsVersion,
      "org.typelevel" %% "cats-effect" % catsVersion,
      "org.typelevel" %% "kittens" % catsVersion,
      "io.circe" %% "circe-core" % circeVersion,
      "io.circe" %% "circe-generic" % circeVersion,
      "io.circe" %% "circe-parser" % circeVersion,
      "com.softwaremill.sttp.client" %% "core" % sttpVersion,
      "com.softwaremill.sttp.client" %% "circe" % sttpVersion,
      "org.scala-lang.modules" %% "scala-xml" % xmlVersion
    ),

    testFrameworks += TestFramework("utest.runner.Framework"),

    scalacOptions ++= Seq("-Xfatal-warnings", "-deprecation")
  )


