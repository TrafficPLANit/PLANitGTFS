# PLANitGTFS

![Master Branch](https://github.com/TrafficPLANit/PLANitGTFS/actions/workflows/maven_master.yml/badge.svg?branch=master)
![Develop Branch](https://github.com/TrafficPLANit/PLANitGTFS/actions/workflows/maven_develop.yml/badge.svg?branch=develop)

Provides a lightweight GTFS API to access GTFS file in a memory model form. 

> Implementation of PLANitGTFS is partially funded by the University of Sydney and the Australian Transport Research Cloud ([ATRC](https://ardc.edu.au/project/australian-transport-research-cloud-atrc/)). ATRC is a project instigated by the Australian Research Data Cloud ([ARDC](www.ardc.edu.au)).

## Development

Repository is LFS enabled for *.zip files, mainly for testing purposes.

### LFS

When dealing with LFS, and getting issues still after installing it and adding (new) file types. This might be due to not having migrated properly. Consider running (replace *pbf with the file type added). This should retroactively fix the repos to support LFS for the given type and should avoid this error when comitting afterwards.

'''
git lfs migrate import --include="*.zip"
'''

### Maven build 

PLANit GTFS has the following PLANit specific dependencies (See pom.xml):

* planit-parentpom
* planit-core
* planit-io
* planit-utils

Dependencies (except parent-pom) will be automatically downloaded from the PLANit website, (www.repository.goplanit.org)[https://repository.goplanit.org], or alternatively can be checked-out locally for local development. The shared PLANit Maven configuration can be found in planit-parent-pom which is defined as the parent pom of each PLANit repository.

> When developing on multiple PLANit projects locally, including the parent-pom; make sure you install the PLANitParentPom pom.xml before conducting a Maven build (in for example Eclipse), otherwise it resorts to the online repository rather then the local one.

### Maven deploy

Distribution management is setup via the parent pom such that Maven deploys this project to the PLANit online repository (also specified in the parent pom). To enable deployment ensure that you setup your credentials correctly in your settings.xml as otherwise the deployment will fail.

### Git Branching model

We adopt GitFlow as per https://nvie.com/posts/a-successful-git-branching-model/
