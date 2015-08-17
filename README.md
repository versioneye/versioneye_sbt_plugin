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

Please take a look how it works on the [VersionEye Maven Plugin](https://github.com/versioneye/versioneye_maven_plugin#mvn-versioneyecreate). 

