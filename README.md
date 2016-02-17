# VersionEye SBT Plugin

This is the VersionEye SBT Plugin for scala projects. This project is under construction.

# Usage

Add to your `project/plugins.sbt` the plugin:

```
addSbtPlugin("com.versioneye" % "sbt-versioneye-plugin" % "0.1.1")
```

Compile it: 

```
sbt compile
```

Enable it within your `build.sbt`

```
enablePlugins(VersionEyePlugin)
```

## Create a VersionEye project

Creates a new VersionEye project and submits the dependencies for analysis. This task
stores the VersionEye project id in a property file (see [Project Id](#project-id)) unless
`updatePropertiesAfterCreate` is set to `false`.

`sbt versioneye:createProject`

## Update the VersionEye project

Updates the VersionEye project with the current dependencies.
Requires the `projectId` to be set (see [Project Id](#project-id))

`sbt versioneye:updateProject`

## Update the VersionEye project and perform a license check

Updates the VersionEye project with the current dependencies and performs
a check agains the license whitelist and unknown projects.
Requires the `projectId` to be set (see [Project Id](#project-id))

`sbt versioneye:licenseCheck`

## Create a pom.json

Creates a `pom.json` file in the `target` dir (usually `target/pom.json`).

`sbt versioneye:json`

# Configuration

## API Key

This plugin can obtain the API key from any of the following property sources (in this precedence):

1. Set `VERSIONEYE_API_KEY=myApiKey` environment variable or the `versioneye.api.key=myApiKey` system property.
2. Configured property file in the SBT build (`apiKey in versioneye := "myApiKey"`)
3. Configured property file in the SBT build (`propertyPath in versioneye := "myfile.properties"`)
4. `src/qa/resources/versioneye.properties`
5. `src/main/resources/versioneye.properties`
6. `${HOME}/.m2/versioneye.properties`

Properties example:

**versioneye.properties**

```
api_key=myApiKey
```

## Project Id

This plugin can obtain the VersionEye project id from any of the following property files (in this precedence):

1. Configured project id in the SBT build (`projectId in versioneye := "55db6cf87a7c24000c03943d"`)
2. Configured property file in the SBT build (`propertyPath in versioneye := "myfile.properties"`)
3. `src/qa/resources/versioneye.properties`
4. `src/main/resources/versioneye.properties`
5. `${HOME}/.m2/versioneye.properties`

The project id of the VersionEye project is stored by default in `src/qa/resources/versioneye.properties`.

Properties example:

**versioneye.properties**

```
project_id=55db6cf87a7c24000c03943d
```

## All configuration properties

| Configuration property        | Description |
|-------------------------------|-------------|
| apiKey                        | Your secret API Key for the VersionEye API. Get it here: https://www.versioneye.com/settings/api|
| apiPath                       | apiPath|
| baseUrl                       | Set the base URL for the VersionEye API. Only needed for VersionEye Enterprise!|
| licenseCheckBreakByUnknown    | If this is true then the goal "versioneye:licenseCheck" will break the build if there is a component without any license.|
| mergeAfterCreate              | If the plugin is executed on a multi module project, the plugin will merge all submodules into the parent project by default. If this behaviour is not desired it can be switched off with this configuration option!|
| nameStrategy                  | If a new project is created the plugin will take the name attribute from the build.sbt as the name of the project at VersionEye. Possible values: name, GA, artifact_id|
| parentArtifactId              | If the plugin is executed on a multi module project, the plugin will merge all submodules into the parent project on the server. the parent project is determined from the build.sbt. However it is possible to set the artifact_id of the parent project explicitly!|
| parentGroupId                 | If the plugin is executed on a multi module project, the plugin will merge all submodules into the parent project on the server. the parent project is determined from the build.sbt. However it is possible to set the group_id of the parent project explicitly!|
| propertiesPath                | propertiesPath|
| proxyHost                     | Set your proxy host name or IP|
| proxyPassword                 | Set your proxy password here|
| proxyPort                     | Set your proxy port here|
| proxyUser                     | Set you proxy user name here|
| skipScopes                    | Comma separated list of scopes which should be ignored by this plugin (e.g. compile, provided)|
| updatePropertiesAfterCreate   | updatePropertiesAfterCreate |
| filterScalaLangDependencies   | By default the scala-library dependency is not tracked. The scala-library dependency can be enabled for tracking by setting this property to `false`. |
| publishCrossVersion           | Append Scala binary version to the artifact_id when publishing this project if set to `true` |





