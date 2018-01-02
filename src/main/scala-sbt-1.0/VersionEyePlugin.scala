package com.versioneye

import java.io._
import java.util.concurrent.TimeUnit

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.fasterxml.jackson.module.scala.experimental.ScalaObjectMapper
import com.versioneye.PropertiesUtil._
import sbt.Keys._
import sbt._

import scala.collection.mutable.ListBuffer
import scalaj.http._

/**
  * @author <a href="mailto:mpaluch@paluch.biz">Mark Paluch</a>
  */
object VersionEyePlugin extends sbt.AutoPlugin {

  val VERSIONEYE_API_KEY_ENV = "VERSIONEYE_API_KEY"
  val VERSIONEYE_API_KEY_PROPERTY = "versioneye.api.key"

  object autoImport {

    val Versioneye = config("versioneye").hide
    lazy val createProject = taskKey[Unit]("Create a new project at VersionEye")
    lazy val updateProject = taskKey[Unit]("Updates an existing project at VersionEye with the dependencies from the current project")
    lazy val licenseCheck = taskKey[Unit](" Updates an existing project at VersionEye with the dependencies from the current project AND  ensures that all used licenses are on a whitelist. If that is not the case it breaks the build.")
    lazy val json = taskKey[Unit]("Create a pom.json containing the direct dependencies")

    val apiKey = settingKey[String]("Your secret API Key for the VersionEye API. Get it here: https://www.versioneye.com/settings/api")
    val baseUrl = settingKey[String]("Set the base URL for the VersionEye API. Only needed for VersionEye Enterprise!")
    val apiPath = settingKey[String]("apiPath")
    val existingProjectId = settingKey[String]("VersionEye project id")
    val propertiesPath = settingKey[String]("propertiesPath")
    val proxyHost = settingKey[String]("Set your proxy host name or IP")
    val proxyPort = settingKey[Int]("Set your proxy port here")
    val proxyUser = settingKey[String]("Set you proxy user name here")
    val proxyPassword = settingKey[String]("Set your proxy password here")
    val updatePropertiesAfterCreate = settingKey[Boolean]("updatePropertiesAfterCreate")
    val mergeAfterCreate = settingKey[Boolean]("If the plugin is executed on a multi module project, the plugin will merge all submodules into the parent project by default. If this behaviour is not desired it can be switched off with this configuration option!")
    val parentGroupId = settingKey[String]("If the plugin is executed on a multi module project, the plugin will merge all submodules into the parent project on the server. the parent project is determined from the build.sbt. However it is possible to set the group_id of the parent project explicitly!")
    val parentArtifactId = settingKey[String]("If the plugin is executed on a multi module project, the plugin will merge all submodules into the parent project on the server. the parent project is determined from the build.sbt. However it is possible to set the artifact_id of the parent project explicitly!")
    val nameStrategy = settingKey[String]("If a new project is created the plugin will take the name attribute from the build.sbt as the name of the project at VersionEye. Possible values: name, GA, artifact_id")
    val trackPlugins = settingKey[Boolean]("By default the plugins who are defined in the build.sbt file are handled like regular dependencies with the \"plugin\" scope. Plugins can be ignored by setting this property to \"false\".")
    val filterScalaLangDependencies = settingKey[Boolean]("By default the scala-library dependency is not tracked. The scala-library dependency can be enabled for tracking by setting this property to \"false\".")
    val licenseCheckBreakByUnknown = settingKey[Boolean]("If this is true then the goal \"versioneye:licenseCheck\" will break the build if there is a component without any license.")
    val skipScopes = settingKey[String]("Comma separated list of scopes which should be ignored by this plugin.")
    val publishCrossVersion = settingKey[Boolean]("Use scala cross version when publishing this artifact")

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
      existingProjectId := "",
      parentArtifactId := "",
      updatePropertiesAfterCreate := true,
      filterScalaLangDependencies := true,
      mergeAfterCreate := true,
      nameStrategy := "name",
      trackPlugins := true,
      licenseCheckBreakByUnknown := false,
      skipScopes := "",
      publishCrossVersion := false
    )
  }

  import autoImport._

  override val projectConfigurations = Seq(Versioneye)

  override lazy val projectSettings =
    versionEyeSettings ++
      inConfig(Versioneye)(Seq(
        json := jsonTask.value,
        createProject := createTask.value,
        updateProject := updateTask.value,
        licenseCheck := licenseCheckTask.value
      )
      ) ++
      inConfig(Versioneye)(Seq())


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

  def getArtifactId(name: String, publishCrossVersion: Boolean, ivyScala: Option[IvyScala]): String = {

    if (publishCrossVersion) {
      val is:IvyScala = ivyScala.get
      return CrossVersion(CrossVersion.binary, is.scalaFullVersion, is.scalaBinaryVersion).map(f => f(name)).getOrElse(name)
    }
    return name
  }

  /**
    * Create a JSON file containing the dependencies.
    */
  private def jsonTask = Def.taskDyn {
    val log = streams.value.log

    val scopes: List[String] = getScopes(skipScopes.value)
    val dependencies = dependencyArray(scopes, libraryDependencies.value, filterScalaLangDependencies.value, scalaModuleInfo.value)
    val pom = Map("name" -> getName(name.value, organization.value, description.value, nameStrategy.value),
      "group_id" -> organization.value, "artifact_id" -> getArtifactId(name.value, publishCrossVersion.value, scalaModuleInfo.value),
      "language" -> "Scala", "prod_type" -> "Sbt", "dependencies" -> dependencies)

    if (dependencies.isEmpty) {
      streams.value.log.info("There are no dependencies in this project !" + organization.value + " / " + name.value)
    }

    Def.task {
      val bytes: Array[Byte] = toJsonBytes(pom)

      val file = target.value / "pom.json"

      IO.write(file, bytes)

      log.info(".")
      log.info("You find your json file here: " + file)
      log.info(".")
    }
  }


  /**
    * Create a VersionEye project.
    */
  private def createTask = Def.taskDyn {
    val log = streams.value.log

    log.info(".")
    log.info("Starting to upload dependencies to " + baseUrl.value + ". This can take a couple seconds ... ")
    log.info(".")

    val scopes: List[String] = getScopes(skipScopes.value)
    val dependencies = dependencyArray(scopes, libraryDependencies.value, filterScalaLangDependencies.value, scalaModuleInfo.value)
    val pom = Map("name" -> getName(name.value, organization.value, description.value, nameStrategy.value),
      "group_id" -> organization.value, "artifact_id" -> getArtifactId(name.value, publishCrossVersion.value, scalaModuleInfo.value),
      "language" -> "Scala", "prod_type" -> "Sbt", "dependencies" -> dependencies)

    if (dependencies.isEmpty) {
      streams.value.log.info("There are no dependencies in this project !" + organization.value + " / " + name.value)
    }

    Def.task {
      val apiKeyValue = getApiKey(apiKey.value, propertiesPath.value, baseDirectory.value)
      val url = getUrl(baseUrl.value, apiPath.value, "/projects?api_key=" + apiKeyValue)
      val bytes = toJsonBytes(pom)

      val proxyConfig = ProxyConfig(proxyHost.value, proxyPort.value, proxyUser.value, proxyPassword.value)

      val request = getHttpRequest(url, proxyConfig).postMulti(MultiPart("upload", "pom.json", "application/json", bytes))
      val response = request.asString

      handleResponseErrorIfAny(response)

      val projectResponse = getResponse(response)

      mergeWithParent(mergeAfterCreate.value, (organization.value, name.value), (parentGroupId.value, parentArtifactId.value), baseUrl.value, apiPath.value, apiKeyValue, projectResponse.getId, proxyConfig)

      if (updatePropertiesAfterCreate.value) {
        writeProperties(projectResponse, getPropertiesFile(propertiesPath.value, baseDirectory.value, false), baseUrl.value)
      }
      prettyPrint(log, baseUrl.value, projectResponse)
    }
  }


  /**
    * Update a VersionEye project.
    */
  private def updateTask = Def.taskDyn {
    val log = streams.value.log

    log.info(".")
    log.info("Starting to upload dependencies to " + baseUrl.value + ". This can take a couple seconds ... ")
    log.info(".")

    val scopes: List[String] = getScopes(skipScopes.value)
    val dependencies = dependencyArray(scopes, libraryDependencies.value, filterScalaLangDependencies.value, scalaModuleInfo.value)
    val pom = Map("name" -> getName(name.value, organization.value, description.value, nameStrategy.value),
      "group_id" -> organization.value, "artifact_id" -> getArtifactId(name.value, publishCrossVersion.value, scalaModuleInfo.value),
      "language" -> "Scala", "prod_type" -> "Sbt", "dependencies" -> dependencies)

    if (dependencies.isEmpty) {
      streams.value.log.info("There are no dependencies in this project !" + organization.value + " / " + name.value)
    }

    Def.task {
      val apiKeyValue = getApiKey(apiKey.value, propertiesPath.value, baseDirectory.value)
      val projectIdValue = getVersionEyeProjectId(existingProjectId.value, propertiesPath.value, baseDirectory.value)
      val url = getUrl(baseUrl.value, apiPath.value, "/projects/", projectIdValue, "?api_key=" + apiKeyValue)
      val bytes = toJsonBytes(pom)

      val proxyConfig = ProxyConfig(proxyHost.value, proxyPort.value, proxyUser.value, proxyPassword.value)

      val request = getHttpRequest(url, proxyConfig).postMulti(MultiPart("project_file", "pom.json", "application/json", bytes))
      val response = request.asString

      handleResponseErrorIfAny(response)

      val projectResponse = getResponse(response)

      mergeWithParent(mergeAfterCreate.value, (organization.value, name.value), (parentGroupId.value, parentArtifactId.value), baseUrl.value, apiPath.value, apiKeyValue, projectResponse.getId, proxyConfig)

      prettyPrint(log, baseUrl.value, projectResponse)
    }
  }

  /**
    * Upload and check license whitelisting of a VersionEye project.
    */
  private def licenseCheckTask = Def.taskDyn {
    val log = streams.value.log

    log.info(".")
    log.info("Starting to upload dependencies to " + baseUrl.value + " for license check. This can take a couple seconds ... ")
    log.info(".")

    val scopes: List[String] = getScopes(skipScopes.value)
    val dependencies = dependencyArray(scopes, libraryDependencies.value, filterScalaLangDependencies.value, scalaModuleInfo.value)
    val pom = Map("name" -> getName(name.value, organization.value, description.value, nameStrategy.value),
      "group_id" -> organization.value, "artifact_id" -> getArtifactId(name.value, publishCrossVersion.value, scalaModuleInfo.value),
      "language" -> "Scala", "prod_type" -> "Sbt", "dependencies" -> dependencies)

    if (dependencies.isEmpty) {
      streams.value.log.info("There are no dependencies in this project !" + organization.value + " / " + name.value)
    }

    Def.task {
      val apiKeyValue = getApiKey(apiKey.value, propertiesPath.value, baseDirectory.value)
      val projectIdValue = getVersionEyeProjectId(existingProjectId.value, propertiesPath.value, baseDirectory.value)
      val url = getUrl(baseUrl.value, apiPath.value, "/projects/", projectIdValue, "?api_key=" + apiKeyValue)
      val bytes = toJsonBytes(pom)

      val proxyConfig = ProxyConfig(proxyHost.value, proxyPort.value, proxyUser.value, proxyPassword.value)

      val request = getHttpRequest(url, proxyConfig).postMulti(MultiPart("project_file", "pom.json", "application/json", bytes))
      val response = request.asString

      handleResponseErrorIfAny(response)

      val projectResponse = getResponse(response)

      if (projectResponse.getLicenses_red > 0) {
        throw new IllegalStateException("Some components violate the license whitelist! " +
          "More details here: " + baseUrl.value + "/user/projects/" + projectResponse.getId)
      }

      if (projectResponse.getLicenses_unknown > 0 && licenseCheckBreakByUnknown.value) {
        throw new IllegalStateException("Some components are without any license! " +
          "More details here: " + baseUrl.value + "/user/projects/" + projectResponse.getId)
      }

      mergeWithParent(mergeAfterCreate.value, (organization.value, name.value), (parentGroupId.value, parentArtifactId.value), baseUrl.value, apiPath.value, apiKeyValue, projectResponse.getId, proxyConfig)

      prettyPrint(log, baseUrl.value, projectResponse)
      log.info("Everything is is fine.")
    }
  }

  def prettyPrint(log: Logger, baseUrl: String, projectResponse: ProjectJsonResponse): Unit = {
    log.info("")
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
      val errMsg = err.map( msg => "Status Code: " + response.statusLine + " -> " + msg).getOrElse("Status Code: " + response.statusLine);
      throw new scala.RuntimeException(errMsg)
    }
  }

  /**
    * Invoke a metadata merge with the parent artifacts.
    */
  def mergeWithParent(mergeAfterCreateValue: Boolean, projectGA: (String, String), parentGA: (String, String), baseUrl: String, apiPath: String, apiKey: String, requestId: String, proxyConfig: ProxyConfig): Unit = {

    if (!mergeAfterCreateValue) {
      return
    }

    if (parentGA._1 == null || parentGA._1.isEmpty || parentGA._2 == null || parentGA._2.isEmpty) {
      return
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
  def getErrorMessage(response: HttpResponse[String]): Option[String] = {
    val body = response.body

    // application/json
    if (response.contentType.isEmpty
      || !"application/json".equals(response.contentType.get)
      || response.body.isEmpty) {
      return None;
    }

    val mapper = new ObjectMapper() with ScalaObjectMapper
    mapper.registerModule(DefaultScalaModule)
    val responseMap = mapper.readValue[Map[String, String]](body)
    if (responseMap.contains("error")) {
      return responseMap.get("error")
    }

    return None
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

  /**
    * Load the API key from project key or a properties file (Home/.m2/, src/qa/resources, src/main/resources)
    */
  def getApiKey(apiKey: String, propertiesFile: String, baseDirectory: File): String = {

    val envApiKey = sys.env.get(VERSIONEYE_API_KEY_ENV)
    val propertiesApiKey = sys.props.get(VERSIONEYE_API_KEY_PROPERTY)

    if(envApiKey.isDefined && propertiesApiKey.isDefined) {
      if(!envApiKey.get.equals(propertiesApiKey.get))
        throw new IllegalStateException("The API key is defined in the environment variable " +
          VERSIONEYE_API_KEY_ENV + " and in the system property " + VERSIONEYE_API_KEY_PROPERTY +
          " with a different setting.")
    }

    if(envApiKey.isDefined){
      return envApiKey.get
    }

    if(propertiesApiKey.isDefined){
      return propertiesApiKey.get
    }

    if (!apiKey.isEmpty) {
      return apiKey
    }

    val option = getPropertiesFileContainingProperty("api_key", propertiesFile, baseDirectory)

    if (option.isEmpty) {
      val file = getPropertiesFile(propertiesFile, baseDirectory, true)
      if (file.exists()) {
        throw new IllegalStateException("versioneye.properties found but without api_key! Read the instructions at https://github.com/versioneye/versioneye_maven_plugin")

      }
      else {
        throw new IllegalStateException("api_key is not specified and versioneye.properties not found")
      }
    }

    return getProperties(option.get).getProperty("api_key")
  }

  /**
    * Load the VersionEye Project id from project key or a properties file (Home/.m2/, src/qa/resources, src/main/resources)
    */
  def getVersionEyeProjectId(projectId: String, propertiesFile: String, baseDirectory: File): String = {
    if (!projectId.isEmpty) {
      return projectId
    }

    val option = getPropertiesFileContainingProperty("project_id", propertiesFile, baseDirectory)

    if (option.isEmpty) {
      val file = getPropertiesFile(propertiesFile, baseDirectory, true)
      if (file.exists()) {
        throw new IllegalStateException("versioneye.properties found but without project_id! Read the instructions at https://github.com/versioneye/versioneye_maven_plugin")

      }
      else {
        throw new IllegalStateException("projectId is not specified and versioneye.properties not found")
      }
    }

    return getProperties(option.get).getProperty("project_id")

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

  def isExcluded(module: ModuleID, filterScalaLangDependencies: Boolean): Boolean = {

    if (!filterScalaLangDependencies) {
      return false
    }

    if ("org.scala-lang" == module.organization && module.name.startsWith("scala-library")) {
      return true
    }

    return false
  }

  def dependencyArray(scopes: List[String], modules: Seq[ModuleID], filterScalaLangDependencies: Boolean, ivyScala: Option[IvyScala]): ListBuffer[Map[String, String]] = {
    val result = ListBuffer[Map[String, String]]()
    modules.foreach(module => {
      val scope = toJsonScope(module.configurations)

      val crossArtifactName: String = CrossVersion.apply(module, ivyScala).map(f => f(module.name)).getOrElse(module.name)

      if (scopes.contains(scope)) {
        var name = module.organization + ":" + crossArtifactName
        val map = Map("name" -> name, "version" -> module.revision, "scope" -> scope)
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
