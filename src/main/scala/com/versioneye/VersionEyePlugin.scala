package com.versioneye

import java.io.{File, _}
import java.util.Properties
import java.util.concurrent.TimeUnit

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.fasterxml.jackson.module.scala.experimental.ScalaObjectMapper
import sbt.Keys._
import sbt._

import scala.collection.mutable.ListBuffer
import scala.io.Source
import scalaj.http._

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
    val propertiesPath = settingKey[String]("propertiesPath")
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
      propertiesPath := "",
      proxyHost := "",
      proxyPort := 0,
      proxyUser := "",
      proxyPassword := "",
      parentGroupId := "",
      parentArtifactId := "",
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
    versionEyeSettings ++
      inConfig(versioneye)(Seq(
        json := jsonTask.value,
        create := createTask.value
      )
      ) ++
      inConfig(versioneye)(Seq())


  /**
   * Resolve the name. Possible strategies: name (default), artifactId, GA
   */
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

  /**
   * Create a JSON file containing the dependencies.
   */
  private def jsonTask = Def.task {
    val log = streams.value.log

    val scopes: List[String] = getScopes(skipScopes.value)
    val dependencies = dependencyArray(scopes, libraryDependencies.value)
    val pom = Map("name" -> getName(name.value, organization.value, description.value, nameStrategy.value),
      "group_id" -> organization.value, "artifact_id" -> name.value,
      "language" -> "Scala", "prod_type" -> "Sbt", "dependencies" -> dependencies)

    val bytes: Array[Byte] = toJsonBytes(pom)

    val file = target.value / "pom.json"

    IO.write(file, bytes)

    log.info(".")
    log.info("You find your json file here: " + file)
    log.info(".")
  }


  /**
   * Create a VersionEye project.
   */
  private def createTask = Def.task {
    val log = streams.value.log

    log.info(".")
    log.info("Starting to upload dependencies. This can take a couple seconds ... ")
    log.info(".")

    val scopes: List[String] = getScopes(skipScopes.value)
    val dependencies = dependencyArray(scopes, libraryDependencies.value)
    val pom = Map("name" -> getName(name.value, organization.value, description.value, nameStrategy.value),
      "group_id" -> organization.value, "artifact_id" -> name.value,
      "language" -> "Scala", "prod_type" -> "Sbt", "dependencies" -> dependencies)

    if (dependencies.isEmpty) {
      log.info("There are no dependencies in this project !" + organization.value + " / " + name.value)
    }

    val apiKeyValue = getApiKey(apiKey.value)
    val url = getUrl(baseUrl.value, apiPath.value, "/projects?api_key=" + apiKeyValue)
    val bytes = toJsonBytes(pom)

    val proxyConfig = ProxyConfig(proxyHost.value, proxyPort.value, proxyUser.value, proxyPassword.value)

    val request = getHttpRequest(url, proxyConfig).postMulti(MultiPart("upload", "pom.json", "application/json", bytes))
    val response = request.asString

    handleResponseErrorIfAny(response)

    val projectResponse = getResponse(response)

    if (mergeAfterCreate.value) {
      mergeWithParent((organization.value, name.value), (parentGroupId.value, parentArtifactId.value), baseUrl.value, apiPath.value, apiKeyValue, projectResponse.getId, proxyConfig)
    }

    if (updatePropertiesAfterCreate.value) {
      PropertiesUtil.writeProperties(projectResponse, PropertiesUtil.getPropertiesPath(propertiesPath.value, baseDirectory.value))
    }
    prettyPrint(log, baseUrl.value, projectResponse)
  }

  def prettyPrint(log: Logger, baseUrl: String, projectResponse: ProjectJsonResponse): Unit = {
    log.info(".")
    log.info("Project name: " + projectResponse.getName)
    log.info("Project id: " + projectResponse.getId)
    log.info("Dependencies: " + projectResponse.getDep_number)
    log.info("Outdated: " + projectResponse.getOut_number)
    log.info("")
    log.info("You can find your updated project here: " + baseUrl + "/user/projects/" + projectResponse.getId)
    log.info("")
  }

  /**
   * Throw a RuntimeException on non-200 status codes.
   */
  def handleResponseErrorIfAny(response: HttpResponse[String]): Unit = {
    if (!response.is2xx) {
      val err = getErrorMessage(response);
      val errMsg: String = "Status Code: " + response.statusLine + " -> " + err
      throw new scala.RuntimeException(errMsg)
    }
  }

  /**
   * Invoke a metadata merge with the parent artifacts.
   */
  def mergeWithParent(projectGA: (String, String), parentGA: (String, String), baseUrl: String, apiPath: String, apiKey: String, requestId: String, proxyConfig: ProxyConfig): Unit = {
    if (parentGA._1 == null || parentGA._1.isEmpty || parentGA._2 == null || parentGA._2.isEmpty) {
      return;
    }

    if ((projectGA._1 == parentGA._1) && (projectGA._1 == parentGA._2)) {
      return
    }

    val parentGroupId = parentGA._1.replaceAll("\\.", "~").replaceAll("/", ":")
    val parentArtifactId = parentGA._2.replaceAll("\\.", "~").replaceAll("/", ":")

    val url = getUrl(baseUrl, apiPath, "/projects/", parentGroupId, "/", parentArtifactId, "/merge_ga/", requestId, "?api_key=", apiKey)

    val response = getHttpRequest(url, proxyConfig).asString
    handleResponseErrorIfAny(response)
  }

  def getResponse(response: HttpResponse[String]): ProjectJsonResponse = {
    val body = response.body

    val mapper = new ObjectMapper() with ScalaObjectMapper
    mapper.registerModule(DefaultScalaModule)
    return mapper.readValue[ProjectJsonResponse](body)
  }

  /**
   * Get error message from a HTTP Response (JSON or raw body)
   * @param response
   */
  def getErrorMessage(response: HttpResponse[String]): String = {
    val body = response.body

    val mapper = new ObjectMapper() with ScalaObjectMapper
    mapper.registerModule(DefaultScalaModule)
    val responseMap = mapper.readValue[Map[String, String]](body)
    if (responseMap.contains("error")) {
      return responseMap.get("error").get
    }

    return body

  }

  def getUrl(values: String*): String = {
    return values.mkString("")
  }

  /**
   * Get a HTTP Request with timeouts and proxy config.
   * @param url
   * @param proxyConfig
   * @return
   */
  def getHttpRequest(url: String, proxyConfig: ProxyConfig): HttpRequest = {
    var http = Http(url);

    http = http.option(HttpOptions.connTimeout(TimeUnit.SECONDS.toMillis(10).toInt))
    http = http.option(HttpOptions.readTimeout(TimeUnit.SECONDS.toMillis(60).toInt))

    if (proxyConfig.host != null && !proxyConfig.host.isEmpty) {
      http = http.proxy(proxyConfig.host, proxyConfig.port)

      if (proxyConfig.username != null && !proxyConfig.username.isEmpty && proxyConfig.password != null && !proxyConfig.password.isEmpty) {
        http = http.header("Proxy-Authorization", "BASIC " + Base64.encodeString(proxyConfig.username + ":" + proxyConfig.password))
      }
    }
    return http
  }

  def getApiKey(value: String): String = {

    if (value == null || value.isEmpty) {
      val msg = "versioneye.properties found but without an API Key! Read the instructions at https://github.com/versioneye/versioneye_maven_plugin"
      throw new IllegalStateException(msg)
    }

    return value
  }

  /**
   * Serialize a map to JSON.
   * @return byte array
   */
  def toJsonBytes(pom: Map[String, Serializable]): Array[Byte] = {
    val mapper = new ObjectMapper()
    mapper.registerModule(DefaultScalaModule)

    val bytes = mapper.writeValueAsBytes(pom)
    bytes
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

    val scopes = Seq("compile", "test", "runtime",
      "provided", "optional")

    var list: List[String] = List()
    if (!skipScopes.isEmpty) {
      list = skipScopes.toLowerCase.split(",").toList
    }

    return scopes.filter((scope) => !list.contains(scope)).toList
  }

  case class ProxyConfig(host: String, port: Int, username: String, password: String)

}
