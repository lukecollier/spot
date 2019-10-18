ThisBuild / scalaVersion := "2.13.1"

ThisBuild / organization := "org.lukecollier"

lazy val catsVersion = "2.0.0"

lazy val spot = (project in file("."))
  .settings(
    name := "Spot",

    libraryDependencies ++= Seq(
      "com.lihaoyi" %% "utest" % "0.7.1" % Test,
      "org.typelevel" %% "cats-core" % catsVersion,
      "org.typelevel" %% "cats-effect" % catsVersion,
      "org.typelevel" %% "kittens" % catsVersion,
      "org.scala-lang.modules" %% "scala-xml" % "1.2.0"
    ),

    testFrameworks += TestFramework("utest.runner.Framework"),

    scalacOptions ++= Seq("-Xfatal-warnings", "-deprecation")
  )


