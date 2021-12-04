name := "fungio"

ThisBuild / baseVersion := "0.1"

ThisBuild / organization := "com.armanbilge"
ThisBuild / publishGithubUser := "armanbilge"
ThisBuild / publishFullName := "Arman Bilge"
ThisBuild / startYear := Some(2021)

ThisBuild / crossScalaVersions := Seq("3.0.2", "2.12.15", "2.13.7")

val graalVersion = "21.3.0"
ThisBuild / githubWorkflowJavaVersions := List(
  JavaSpec.graalvm(graalVersion, "11"),
  JavaSpec.graalvm(graalVersion, "17")
)

replaceCommandAlias(
  "ci",
  "; project /; headerCheckAll; scalafmtCheckAll; javafmtCheckAll; scalafmtSbtCheck; clean; testIfRelevant; mimaReportBinaryIssuesIfRelevant"
)

val catsEffectVersion = "3.3.0"
val disciplineSpecs2Version = "1.2.5"

lazy val root =
  project.in(file(".")).aggregate(core).enablePlugins(NoPublishPlugin)

lazy val core = project
  .in(file("core"))
  .settings(
    name := "fungio",
    libraryDependencies ++= Seq(
      "org.typelevel" %% "cats-effect-kernel" % catsEffectVersion,
      "org.typelevel" %% "cats-effect-laws" % catsEffectVersion % Test,
      "org.typelevel" %% "discipline-specs2" % disciplineSpecs2Version % Test
    )
  )

lazy val benchmarks = project
  .in(file("benchmarks"))
  .dependsOn(core)
  .settings(name := "fungio-benchmarks")
  .enablePlugins(NoPublishPlugin, JmhPlugin)
