sbtPlugin := true
organization := "com.versioneye"
name := "sbt-versioneye-plugin"
version := "0.2.1-SNAPSHOT"
organizationHomepage := Some(new URL("https://www.versioneye.com"))
description := "This is the sbt plugin for https://www.VersionEye.com. It allows you to create and update a project at VersionEye. You can find a complete documentation of this project on GitHub: https://github.com/versioneye/versioneye_sbt_plugin."
startYear := Some(2015)

scalaVersion := "2.12.4"

crossSbtVersions := Vector("0.13.16", "1.0.4")


libraryDependencies ++= Seq("com.fasterxml.jackson.module" %%  "jackson-module-scala" % "2.9.1",
                            "org.scalaj" %% "scalaj-http" % "2.3.0")

publishArtifact in Test := false
publishMavenStyle := true
pomIncludeRepository := { _ => false }

// scripted-plugin
scriptedLaunchOpts := { scriptedLaunchOpts.value ++
  Seq("-Xmx1024M", "-Dplugin.version=" + version.value)
}
scriptedBufferLog := false
watchSources ++= { sourceDirectory.map { path => (path ** "*").get }.value }

// maven repositories
resolvers ++= Seq(
  Opts.resolver.sonatypeReleases,
  "sonatype.repo" at "https://oss.sonatype.org/content/groups/public",
  "Local Maven Repository" at ("file://" + Path.userHome.absolutePath + "/.m2/repository")
)

publishTo := {
  // Some(Resolver.file("file",  new File(Path.userHome.absolutePath+"/.m2/repository")))
  val nexus = "https://oss.sonatype.org/"
  if (version.value.trim.endsWith("SNAPSHOT"))
    Some("snapshots" at nexus + "content/repositories/snapshots")
  else
    Some("releases" at nexus + "service/local/staging/deploy/maven2")
}

pomExtra := (
  <url>https://github.com/versioneye/versioneye_sbt_plugin</url>
  <licenses>
    <license>
      <name>MIT</name>
      <url>http://choosealicense.com/licenses/mit/</url>
      <distribution>repo</distribution>
    </license>
  </licenses>
  <scm>
    <url>git@github.com:versioneye/sbt-growl-plugin.git</url>
    <connection>scm:git:git@github.com:softprops/versioneye_sbt_plugin.git</connection>
  </scm>
  <developers>
    <developer>
      <id>mp911de</id>
      <name>Mark Paluch</name>
      <url>https://github.com/mp911de</url>
    </developer>
  </developers>
)

