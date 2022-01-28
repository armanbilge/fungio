name := "fungio"

ThisBuild / tlBaseVersion := "0.1"

ThisBuild / organization := "com.armanbilge"
ThisBuild / organizationName := "Arman Bilge"
ThisBuild / developers += tlGitHubDev("armanbilge", "Arman Bilge")
ThisBuild / startYear := Some(2021)

ThisBuild / tlCiReleaseTags := false
ThisBuild / tlCiReleaseBranches := Seq.empty
ThisBuild / githubWorkflowBuild ~= { steps =>
  steps.map {
    case step @ WorkflowStep.Sbt(
          "headerCheckAll" :: "scalafmtCheckAll" :: tail,
          _,
          _,
          _,
          _,
          _) =>
      step.copy(commands = "headerCheckAll" :: "scalafmtCheckAll" :: "javafmtCheckAll" :: tail)
    case step => step
  }
}

ThisBuild / crossScalaVersions := Seq("3.0.2", "2.12.15", "2.13.7")

val graalVersion = "22.0.0"
ThisBuild / githubWorkflowJavaVersions := List(
  JavaSpec.graalvm(graalVersion, "11"),
  JavaSpec.graalvm(graalVersion, "17")
)

val catsEffectVersion = "3.3.4"
val disciplineSpecs2Version = "1.2.5"

lazy val root =
  project.in(file(".")).aggregate(core, benchmarks).enablePlugins(NoPublishPlugin)

lazy val core = project
  .in(file("core"))
  .settings(
    name := "fungio",
    libraryDependencies ++= Seq(
      "org.typelevel" %% "cats-effect-kernel" % catsEffectVersion,
      "org.typelevel" %% "cats-effect-laws" % catsEffectVersion % Test,
      "org.typelevel" %% "cats-effect-testkit" % catsEffectVersion % Test,
      "org.typelevel" %% "discipline-specs2" % disciplineSpecs2Version % Test
    ),
    javacOptions ++= modules,
    javaOptions ++= modules,
    fork := true
  )

lazy val benchmarks = project
  .in(file("benchmarks"))
  .dependsOn(core)
  .settings(
    name := "fungio-benchmarks",
    libraryDependencies += "org.typelevel" %% "cats-effect" % catsEffectVersion,
    javaOptions ++= modules
  )
  .enablePlugins(NoPublishPlugin, JmhPlugin)

lazy val modules = Seq(
  "--add-modules=org.graalvm.truffle",
  "--add-exports=org.graalvm.truffle/com.oracle.truffle.api=ALL-UNNAMED",
  "--add-exports=org.graalvm.truffle/com.oracle.truffle.api.frame=ALL-UNNAMED",
  "--add-exports=org.graalvm.truffle/com.oracle.truffle.api.nodes=ALL-UNNAMED"
)
