package com.versioneye

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

/**
 * Java representation of the project JSON response from VersionEye API.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
class ProjectJsonResponse {
  private var name: String = null
  private var id: String = null
  private var dep_number: Integer = null
  private var out_number: Integer = null
  private var licenses_red: Integer = 0
  private var licenses_unknown: Integer = 0

  def getLicenses_red: Integer = {
    return licenses_red
  }

  def setLicenses_red(licenses_red: Integer) {
    this.licenses_red = licenses_red
  }

  def getLicenses_unknown: Integer = {
    return licenses_unknown
  }

  def setLicenses_unknown(licenses_unknown: Integer) {
    this.licenses_unknown = licenses_unknown
  }

  def getName: String = {
    return name
  }

  def setName(name: String) {
    this.name = name
  }

  def getId: String = {
    return id
  }

  def setId(id: String) {
    this.id = id
  }

  def getDep_number: Integer = {
    return dep_number
  }

  def setDep_number(dep_number: Integer) {
    this.dep_number = dep_number
  }

  def getOut_number: Integer = {
    return out_number
  }

  def setOut_number(out_number: Integer) {
    this.out_number = out_number
  }
}
