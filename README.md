# VersionEye SBT Plugin

This is teh VersionEye SBT Plugin for scala projects. This project is under construtciton. 

## Requirements 

The VersionEye SBT Plugin should work similar to the [VersionEye Maven Plugin](https://github.com/versioneye/versioneye_maven_plugin). The SBT Plugin should: 

 1. resolve all dependencies locally
 2. generate a `pom.json` file like described [here](https://github.com/versioneye/pom_json_format)
 3. and upload the `pom.json` file to one of the Endpoints at the [VersionEye API](https://www.versioneye.com/api/). 
 
For the beginning this 3 feature should be implemented: 

 1. create a new project at VersionEye 
 2. update an existing project at VersionEye 
 3. check the license whitelist for the project at VersionEye. 
 
