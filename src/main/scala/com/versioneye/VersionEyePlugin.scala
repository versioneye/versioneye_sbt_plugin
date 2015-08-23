package com.versioneye

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import sbt.Keys._
import sbt._

import scala.collection.mutable.ListBuffer

/**
 * @author <a href="mailto:mpaluch@paluch.biz">Mark Paluch</a>
 */
object VersionEyePlugin extends sbt.AutoPlugin {

  object autoImport {

    val versioneye = config("versioneye").hide
    lazy val create = taskKey[Unit]("Create a new project at VersionEye")
    lazy val json = taskKey[Unit]("Create a pom.json containing the direct dependencies")

    val apiKey = settingKey[String]("Your secret API Key for the VersionEye API. Get it here: https://www.versioneye.com/settings/api")
    val baseUrl = settingKey[String]("Set the base URL for the VersionEye API. Only needed for VersionEye Enterprise!")
    val apiPath = settingKey[String]("apiPath")
    val proxyHost = settingKey[String]("Set your proxy host name or IP")
    val proxyPort = settingKey[Int]("Set your proxy port here.")
    val proxyUser = settingKey[String]("Set you proxy user name here")
    val proxyPassword = settingKey[String]("Set your proxy password here")
    val updatePropertiesAfterCreate = settingKey[Boolean]("updatePropertiesAfterCreate")
    val mergeAfterCreate = settingKey[Boolean]("If the plugin is executed on a multi module project, the plugin will merge all submodules into the parent project by default. If this behaviour is not desired it can be switched off with this configuration option!")
    val parentGroupId = settingKey[String]("If the plugin is executed on a multi module project, the plugin will merge all submodules into the parent project on the server. the parent project is determined from the build.sbt. However it is possible to set the group_id of the parent project explicitly!")
    val parentArtifactId = settingKey[String]("If the plugin is executed on a multi module project, the plugin will merge all submodules into the parent project on the server. the parent project is determined from the build.sbt. However it is possible to set the artifact_id of the parent project explicitly!")
    val nameStrategy = settingKey[String]("If a new project is created the plugin will take the name attribute from the build.sbt as the name of the project at VersionEye. Possible values: name, GA, artifact_id")
    val trackPlugins = settingKey[Boolean]("By default the plugins who are defined in the build.sbt file are handled like regular dependencies with the \"plugin\" scope. Plugins can be ignored by setting this property to \"false\".")
    val licenseCheckBreakByUnknown = settingKey[Boolean]("If this is true then the goal \"versioneye:licenseCheck\" will break the build if there is a component without any license.")
    val skipScopes = settingKey[String]("Comma seperated list of scopes which should be ignored by this plugin.")

    // default values for the tasks and settings
    lazy val versionEyeSettings: Seq[Def.Setting[_]] = Seq(
      apiKey := "",
      baseUrl := "https://www.versioneye.com",
      apiPath := "/api/v2",
      proxyHost := "",
      proxyPort := 0,
      proxyUser := "",
      proxyPassword := "",
      updatePropertiesAfterCreate := true,
      mergeAfterCreate := true,
      nameStrategy := "name",
      trackPlugins := true,
      licenseCheckBreakByUnknown := false,
      skipScopes := ""
    )
  }

  import autoImport._

  override val projectConfigurations = Seq(versioneye)

  override lazy val projectSettings =
    containerSettings(versioneye) ++
      inConfig(versioneye)(Seq())

  def containerSettings(conf: Configuration) =
    versionEyeSettings ++
      inConfig(conf)(Seq(
        json := jsonTask.value
      ))


  def getName(name: String, organization: String, description: String, nameStrategy: String): String = {

    var result = description

    if (result == null || result.isEmpty || nameStrategy == "artifact_id") {
      result = name
    }
    else if (nameStrategy == "GA") {
      result = name + "/" + organization
    }

    return result

  }

  private def jsonTask = Def.task {
    val log = streams.value.log
    val conf = configuration.value

    val scopes: List[String] = getScopes(skipScopes.value)
    val dependencies = dependencyArray(scopes, libraryDependencies.value)
    val pom = Map("name" -> getName(name.value, organization.value, description.value, nameStrategy.value),
      "group_id" -> organization.value, "artifact_id" -> name.value,
      "language" -> "Scala", "prod_type" -> "Sbt", "dependencies" -> dependencies)

    if (dependencies.isEmpty) {
      log.info("There are no dependencies in this project !" + organization.value + " / " + name.value)
    }

    val mapper = new ObjectMapper()
    mapper.registerModule(DefaultScalaModule)

    val bytes = mapper.writeValueAsBytes(pom)

    val file = target.value / "pom.json"

    IO.write(file, bytes)

    log.info(".")
    log.info("You find your json file here: " + file)
    log.info(".")
  }


  def dependencyArray(scopes: List[String], modules: Seq[ModuleID]): ListBuffer[Map[String, String]] = {
    val result = ListBuffer[Map[String, String]]()
    modules.foreach(module => {
      val scope = toJsonScope(module.configurations)
      if (scopes.contains(scope)) {
        val map = Map("name" -> module.organization, "version" -> module.revision, "scope" -> scope)
        result += map
      }
    }

    )

    return result
  }

  def toJsonScope(scope: Option[String]): String = {
    return scope.getOrElse("compile")
  }


  def getScopes(skipScopes: String): List[String] = {

    val scopes = Seq("compile","test", "runtime",
      "provided","optional")

    var list: List[String] = List()
    if (!skipScopes.isEmpty) {
      list = skipScopes.toLowerCase.split(",").toList
    }

    return scopes.filter((scope) => !list.contains(scope)).toList
  }

}
