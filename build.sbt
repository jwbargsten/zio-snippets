val scala3Version = "3.6.4"

lazy val root = project
  .in(file("."))
  .settings(
    name := "ZIO snippets",
    version := "0.1.0-SNAPSHOT",
    scalaVersion := scala3Version,
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio-test" % Version.zio,
      "dev.zio" %% "zio-json" % Version.zioJson,
      "dev.zio" %% "zio-logging-slf4j" % Version.zioLogging,
      "dev.zio" %% "zio-interop-cats" % Version.zioInteropCats,
      "dev.zio" %% "zio" % Version.zio,
      "dev.zio" %% "zio-streams" % Version.zio,
      "com.github.pureconfig" %% "pureconfig-core" % Version.pureconfig,
      "com.github.pureconfig" %% "pureconfig-generic-scala3" % Version.pureconfig,
      "com.github.jatcwang" %% "difflicious-core" % Version.difflicious,
      "com.github.jatcwang" %% "difflicious-cats" % Version.difflicious
    ),
    addCommandAlias("format", "scalafmtAll;scalafmtSbt")
  )
