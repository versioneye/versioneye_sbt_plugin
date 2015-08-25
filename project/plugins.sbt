addSbtPlugin("com.typesafe.sbt" % "sbt-pgp" % "0.8.3")

// scripted for plugin testing
libraryDependencies <+= (sbtVersion) { sv =>
  "org.scala-sbt" % "scripted-plugin" % sv
}

