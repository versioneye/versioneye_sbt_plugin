name := "test"

version := "0.0.1-SNAPSHOT"

enablePlugins(VersionEyePlugin)

libraryDependencies += "org.sonatype.plugins" % "nexus-staging-maven-plugin" % "1.6.6" % "test"

libraryDependencies +=  "me.lessis" %% "meow" % "0.1.1"


