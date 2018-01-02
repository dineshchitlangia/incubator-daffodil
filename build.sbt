lazy val genManaged = taskKey[Unit]("Generate managed sources and resources")
lazy val genProps = taskKey[Seq[File]]("Generate properties scala source")
lazy val genSchemas = taskKey[Seq[File]]("Generated DFDL schemas")

lazy val TestDebug = config("debug") extend(Test)


lazy val daffodil         = Project("daffodil", file(".")).configs(TestDebug)
                              .aggregate(macroLib, propgen, lib, io, runtime1, runtime1Unparser, core, japi, sapi, tdml, test, testIBM1, tutorials, testStdLayout)
                              .settings(commonSettings, nopublish)

lazy val macroLib         = Project("daffodil-macro-lib", file("daffodil-macro-lib")).configs(TestDebug)
                              .settings(commonSettings, nopublish)
                              .settings(libraryDependencies += "org.scala-lang" % "scala-reflect" % scalaVersion.value)

lazy val propgen          = Project("daffodil-propgen", file("daffodil-propgen")).configs(TestDebug)
                              .settings(commonSettings, nopublish)

lazy val lib              = Project("daffodil-lib", file("daffodil-lib")).configs(TestDebug)
                              .dependsOn(macroLib % "compile-internal, test-internal")
                              .settings(commonSettings, libManagedSettings, usesMacros)

lazy val io               = Project("daffodil-io", file("daffodil-io")).configs(TestDebug)
                              .dependsOn(lib, macroLib % "compile-internal, test-internal")
                              .settings(commonSettings, usesMacros)

lazy val runtime1         = Project("daffodil-runtime1", file("daffodil-runtime1")).configs(TestDebug)
                              .dependsOn(io, lib % "test->test")
                              .settings(commonSettings)

lazy val runtime1Unparser = Project("daffodil-runtime1-unparser", file("daffodil-runtime1-unparser")).configs(TestDebug)
                              .dependsOn(runtime1, lib % "test->test", runtime1 % "test->test")
                              .settings(commonSettings)

lazy val core             = Project("daffodil-core", file("daffodil-core")).configs(TestDebug)
                              .dependsOn(runtime1Unparser, lib % "test->test", runtime1 % "test->test")
                              .settings(commonSettings)

lazy val japi             = Project("daffodil-japi", file("daffodil-japi")).configs(TestDebug)
                              .dependsOn(core)
                              .settings(commonSettings)

lazy val sapi             = Project("daffodil-sapi", file("daffodil-sapi")).configs(TestDebug)
                              .dependsOn(core)
                              .settings(commonSettings)

lazy val tdml             = Project("daffodil-tdml", file("daffodil-tdml")).configs(TestDebug)
                              .dependsOn(core, core % "test->test")
                              .settings(commonSettings)

lazy val cli              = Project("daffodil-cli", file("daffodil-cli")).configs(TestDebug)
                              .dependsOn(tdml, sapi, japi) // causes sapi/japi to be pulled in to the helper zip/tar
                              .settings(commonSettings, nopublish)
                              .settings(libraryDependencies ++= Dependencies.cli) 

lazy val test             = Project("daffodil-test", file("daffodil-test")).configs(TestDebug)
                              .dependsOn(tdml, core % "test->test")
                              .settings(commonSettings, nopublish)

lazy val testIBM1         = Project("daffodil-test-ibm1", file("daffodil-test-ibm1")).configs(TestDebug)
                              .dependsOn(tdml)
                              .settings(commonSettings, nopublish)

lazy val tutorials        = Project("daffodil-tutorials", file("tutorials")).configs(TestDebug)
                              .dependsOn(tdml)
                              .settings(commonSettings, nopublish)

lazy val testStdLayout    = Project("daffodil-test-stdLayout", file("test-stdLayout")).configs(TestDebug)
                              .dependsOn(tdml, core % "test->test")
                              .settings(commonSettings, nopublish)



lazy val commonSettings = Seq(
  organization := "edu.illinois.ncsa",
  version := "2.1.0-SNAPSHOT",
  scalaVersion := "2.11.8",
  scalacOptions ++= Seq(
    "-deprecation",
    "-language:experimental.macros",
    "-unchecked",
    "-Xfatal-warnings",
    "-Xxml:-coalescing",
    "-Ybackend:GenBCode",
    "-Ydead-code",
    "-Yinline" ,
    "-Yinline-warnings",
    "-Yopt:inline-global",
    "-Yopt-warnings",
    "-Ywarn-inaccessible",
    "-Ywarn-infer-any",
    "-Ywarn-nullary-override",
    "-Ywarn-unused",
    "-Ywarn-unused-import"
  ),
  logBuffered := true,
  transitiveClassifiers := Seq("sources", "javadoc"),
  retrieveManaged := true,
  exportJars := true,
  exportJars in Test := false,
  publishMavenStyle := true,
  publishArtifact in Test := false,
  pomIncludeRepository in ThisBuild := { _ => false },
  scmInfo := Some(
    ScmInfo(
      browseUrl = url("https://gitbox.apache.org/repos/asf/incubator-daffodil.git"),
      connection = "scm:git:https://gitbox.apache.org/repos/asf/incubator-daffodil.git"
    )
  ),
  licenses := Seq("University of Illinois/NCSA Open Source License" -> url("http://opensource.org/licenses/UoI-NCSA.php")),
  homepage := Some(url("https://daffodil.apache.org")),
  initialize := {
    val _ = initialize.value
    if (sys.props("java.specification.version") != "1.8") {
      sys.error("Java 8 is required for this project.")
    }
  },
  unmanagedBase := baseDirectory.value / "lib" / "jars",
  sourceManaged := baseDirectory.value / "src_managed",
  resourceManaged := baseDirectory.value / "resource_managed",
  libraryDependencies ++= Dependencies.common,
) ++ debugTestSettings

lazy val debugTestSettings = inConfig(TestDebug)(Defaults.testSettings ++ Seq(
  sourceDirectory := baseDirectory.value / "src" / "test",
  scalaSource := sourceDirectory.value / "scala-debug",
  exportJars := false,
))

lazy val nopublish = Seq(
  publish := {},
  publishLocal := {},
  publishM2 := {},
  skip in publish := true
)

lazy val usesMacros = Seq(
  mappings in (Compile, packageBin) ++= mappings.in(macroLib, Compile, packageBin).value,
  mappings in (Compile, packageSrc) ++= mappings.in(macroLib, Compile, packageSrc).value
)

lazy val libManagedSettings = Seq(
  genManaged := {
    (genProps in Compile).value
    (genSchemas in Compile).value
  },
  genProps in Compile := {
    val cp = (dependencyClasspath in Runtime in propgen).value
    val inSrc = (sources in Compile in propgen).value
    val inRSrc = (resources in Compile in propgen).value
    val stream = (streams in propgen).value
    val outdir = (sourceManaged in Compile).value
    val filesToWatch = (inSrc ++ inRSrc).toSet
    val cachedFun = FileFunction.cached(stream.cacheDirectory / "propgen") { (in: Set[File]) =>
      val mainClass = "edu.illinois.ncsa.daffodil.propGen.PropertyGenerator"
      val out = new java.io.ByteArrayOutputStream()
      val forkOpts = ForkOptions()
                       .withOutputStrategy(Some(CustomOutput(out)))
                       .withBootJars(cp.files.toVector)
      val ret = new Fork("java", Some(mainClass)).fork(forkOpts, Seq(outdir.toString)).exitValue()
      if (ret != 0) {
        sys.error("Failed to generate code")
      }
      val bis = new java.io.ByteArrayInputStream(out.toByteArray)
      val isr = new java.io.InputStreamReader(bis)
      val br = new java.io.BufferedReader(isr)
      val iterator = Iterator.continually(br.readLine()).takeWhile(_ != null)
      val files = iterator.map { f =>
        stream.log.info("Generated %s".format(f))
        new File(f)
      }.toSet
      files
    }
    cachedFun(filesToWatch).toSeq
  },
  genSchemas in Compile := {
    val inRSrc = (resources in Compile in propgen).value
    val stream = (streams in propgen).value
    val outdir = (resourceManaged in Compile).value
    val filesToWatch = inRSrc.filter{_.isFile}.toSet
    val cachedFun = FileFunction.cached(stream.cacheDirectory / "schemasgen") { (schemas: Set[File]) =>
      schemas.map { schema =>
        val out = outdir / "edu" / "illinois" / "ncsa" / "daffodil" / "xsd" / schema.getName
        IO.copyFile(schema, out)
        stream.log.info("Generated %s".format(out))
        out
      }
    }
    cachedFun(filesToWatch).toSeq
  },
  sourceGenerators in Compile += (genProps in Compile).taskValue,
  resourceGenerators in Compile += (genSchemas in Compile).taskValue
)
