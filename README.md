# VersionEye SBT Plugin

This is teh VersionEye SBT Plugin for scala projects. This project is under construtciton. 

## Requirements 

The VersionEye SBT Plugin should work similar to the [VersionEye Maven Plugin](https://github.com/versioneye/versioneye_maven_plugin). The SBT Plugin should: 

 1. resolve all dependencies locally
 2. generate a `pom.json` file like described [here](https://github.com/versioneye/pom_json_format)
 3. and upload the `pom.json` file to one of the Endpoints at the [VersionEye API](https://www.versioneye.com/api/). 
 
For the beginning this 3 features should be implemented: 

 1. create a new project at VersionEye 
 2. update an existing project at VersionEye 
 3. check the license whitelist for the project at VersionEye. 
 
### Creating a new project 

The VersionEye SBT Plugin should be able to create a new project at VersionEye based on an SBT project file. Ideally a command like this: 

```
sbt versioneye:create
```

would resolve all dependencies locally, create the `pom.json` file and upload it via HTTP POST to this endpoint: 

```
POST /v2/projects
```

The response from the server is a JSON as well, it contains the new project ID and some additional informations. The SBT plugin should store the project ID somewhere, for example in a `versioneye.properties` file. Please take a look how it works on the [VersionEye Maven Plugin](https://github.com/versioneye/versioneye_maven_plugin#mvn-versioneyecreate). 

### Updating a project 

The VersionEye SBT Plugin should be able to update an existing project at VersionEye with the dependencies from the current SBT project file. Ideally a command like this: 

```
sbt versioneye:update
```

would read the project ID from a properties file, for example `versioneye.properites`, resolve all dependencies locally, create the `pom.json` file and upload it via HTTP POST to this endpoint: 

```
POST /v2/projects/PROJECT_ID
```

The response from the server is a JSON as well, it contains the status of the project. The SBT plugin should out put the number of out-dated dependencies in the console. Please take a look how it works on the [VersionEye Maven Plugin](https://github.com/versioneye/versioneye_maven_plugin#mvn-versioneyeupdate). 

### License Check

If there is a license whitelist on the server and it is marked as default, VersionEye will check on each update the license whitelist against all dependencies of a project. The VersionEye SBT Plugin should be able to check if there is a license violaiton. Ideally a command like this: 

```
sbt versioneye:licenseCheck
```

would read the project ID from a properties file, for example `versioneye.properites`, resolve all dependencies locally, create the `pom.json` file and upload it via HTTP POST to this endpoint: 

```
POST /v2/projects/PROJECT_ID
```

The response from the server is a JSON as well, it contains a property `licenses_red` with the number of dependencies which violate the license whitelist on the server. If the value is 0 everything is OK. If the value is greater than 0 there are as many dependencies in the project which violate the license whitelist. In that case the SBT plugin should exit with an Exception, so that it would break the build on any CI Server. Please take a look how it works on the [VersionEye Maven Plugin](https://github.com/versioneye/versioneye_maven_plugin#mvn-versioneyelicensecheck).



