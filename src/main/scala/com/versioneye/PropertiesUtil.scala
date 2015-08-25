package com.versioneye

import java.io.{File, FileOutputStream}
import java.util.Properties

import scala.io.Source

/**
 * @author <a href="mailto:mpaluch@paluch.biz">Mark Paluch</a>
 */
object PropertiesUtil {

  protected val propertiesFile: String = "versioneye.properties"

  def writeProperties(response: ProjectJsonResponse, propertiesFile: File): Unit = {
    var properties: Properties = null

    if (!propertiesFile.exists()) {
      createPropertiesFile(propertiesFile)
      properties = new Properties()
    }
    else {
      properties = loadProperties(propertiesFile)
    }


    if (response.getId != null) {
      properties.setProperty("project_id", response.getId)
    }

    val fos = new FileOutputStream(propertiesFile)
    properties.store(fos, " Properties for https://www.VersionEye.com")
    fos.close()
  }

  def getProperties(propertiesFile: File): Properties = {
    return loadProperties(propertiesFile)
  }

  private def loadProperties(file: File): Properties = {
    if (!file.exists) {
      return null
    }

    val properties = new Properties()
    val reader = Source.fromFile(file).reader()
    properties.load(reader)
    reader.close()
    return properties
  }

  private def createPropertiesFile(file: File) {
    val parent: File = file.getParentFile
    if (!parent.exists) {
      parent.mkdirs
    }
    file.createNewFile
  }

  def getPropertiesFile(properties: String, projectDirectory: File, withHome: Boolean): File = {
    val candidates = getPropertyFileCandidates(properties, projectDirectory, false)
    val firstFile = candidates.find(_.exists())
    return firstFile.orElse(candidates.find(!_.exists())).get
  }

  def containing(key: String, file: File): Boolean = {
    if (!file.exists()) {
      return false
    }

    return loadProperties(file).containsKey(key)
  }

  def getPropertiesFileContainingProperty(key: String, properties: String, projectDirectory: File): Option[File] = {
    val candidates = getPropertyFileCandidates(properties, projectDirectory, true)
    val firstFile = candidates.find(containing(key, _))
    return firstFile
  }

  def getPropertyFileCandidates(properties: String, projectDirectory: File, withHome: Boolean): Seq[File] = {
    if (!properties.isEmpty) {
      return Seq(new File(properties));
    }

    var qaResources = new File(projectDirectory, "src/qa/resources/" + propertiesFile)
    var mainResources = new File(projectDirectory, "src/main/resources/" + propertiesFile)
    var userHome = new File(System.getProperty("user.home") + "/.m2/" + propertiesFile)

    if (withHome)
      return Seq(qaResources, mainResources, userHome)
    else
      return Seq(qaResources, mainResources)
  }

}
