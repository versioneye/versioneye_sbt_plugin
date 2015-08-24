package com.versioneye

import java.io.{File, FileOutputStream}
import java.util.Properties

import scala.io.Source

/**
 * @author <a href="mailto:mpaluch@paluch.biz">Mark Paluch</a>
 */
object PropertiesUtil {

  protected val propertiesFile: String = "versioneye.properties"


  def writeProperties(response: ProjectJsonResponse, path: String): Unit = {
    val properties = fetchProjectProperties(path)
    if (response.getId != null) {
      properties.setProperty("project_id", response.getId)
    }

    val file = new File(path)
    val fos = new FileOutputStream(file)
    properties.store(fos, " Properties for http://www.VersionEye.com")

  }

  private def fetchProjectProperties(path: String): Properties = {
    val file: File = new File(path)
    if (!file.exists) createPropertiesFile(file)


    val properties = new Properties()
    properties.load(Source.fromFile(file).reader())
    return properties
  }

  private def createPropertiesFile(file: File) {
    val parent: File = file.getParentFile
    if (!parent.exists) {
      parent.mkdirs
    }
    file.createNewFile
  }


  def getPropertiesPath(properties: String, projectDirectory: File): String = {

    if (!properties.isEmpty) {
      return properties;
    }

    var propertiesPath: String = "src/qa/resources/" + propertiesFile
    var file: File = new File(projectDirectory, propertiesPath)
    if (!file.exists) {
      propertiesPath = "src/main/resources/" + propertiesFile
      file = new File(projectDirectory, propertiesPath)
    }
    if (!file.exists) {
      propertiesPath = System.getProperty("user.home") + "/.m2/" + propertiesFile
      file = new File(propertiesPath)
    }
    if (!file.exists) {
      propertiesPath = "src/main/resources/" + propertiesFile
      file = new File(projectDirectory, propertiesPath)
    }

    return propertiesPath
  }

}
