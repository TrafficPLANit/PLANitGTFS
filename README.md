# PLANitGTFS

Provides a lightweight GTFS API to access GTFS file in a memory model form.

> Implementation of PLANitGTFS is partially funded by the University of Sydney and the Australian Transport Research Cloud ([ATRC](https://ardc.edu.au/project/australian-transport-research-cloud-atrc/)). ATRC is a project instigated by the Australian Research Data Cloud ([ARDC](www.ardc.edu.au)).

## Development

### Maven build 

PLANit GTFS has the following PLANit specific dependencies (See pom.xml):

* planit-parentpom
* planit-core
* planit-utils

Dependencies (except parent-pom) will be automatically downloaded from the PLANit website, (www.repository.goplanit.org)[https://repository.goplanit.org], or alternatively can be checked-out locally for local development. The shared PLANit Maven configuration can be found in planit-parent-pom which is defined as the parent pom of each PLANit repository.

Since the repo depends on the parent-pom to find its (shared) repositories, we must let Maven find the parent-pom first, either:

* localy clone the parent pom repo and run mvn install on it before conducting a Maven build, or
* add the parent pom repository to your maven (user) settings.xml by adding it to a profile like the following

```xml
  <profiles>
    <profile>
      <activation>
        <property>
          <name>!skip</name>
        </property>
      </activation>
    
      <repositories>
        <repository>
          <id>planit-repository.goplanit.org</id>
          <name>PLANit Repository</name>
          <url>http://repository.goplanit.org</url>
        </repository>     
      </repositories>
    </profile>
  </profiles>
```

### Maven deploy

Distribution management is setup via the parent pom such that Maven deploys this project to the PLANit online repository (also specified in the parent pom). To enable deployment ensure that you setup your credentials correctly in your settings.xml as otherwise the deployment will fail.

### Git Branching model

We adopt GitFlow as per https://nvie.com/posts/a-successful-git-branching-model/