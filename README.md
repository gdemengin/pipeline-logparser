# pipeline-logparser
a library to parse and filter logs
  * implementation of https://stackoverflow.com/a/57351397
  * workaround for https://issues.jenkins-ci.org/browse/JENKINS-54304

## content
it allows
- to add branch prefix [branchName] in front of each line of the logs belonging to a parallel branch
  * (to get back logs similar to what they used to be before version 2.25 of workflow-job plugin)
- to filter logs by branchName
- to show name of parent branches (as prefix in the logs) for nested branches
- to hide VT100 markups
- to get log and branches internal ids
- to archive files in job artifacts without having to allocate a node (same as ArchiveArtifacts but without node() scope)

## installation

it is meant to be used as a "Global Pipeline Library"
- configured by jenkins administrator ("Manage jenkins > Configure System > Global Pipeline Library")
- cf https://jenkins.io/doc/book/pipeline/shared-libraries/

NB:
  * it's also possible to copy the code in a Jenkinsfile and use functions from there
  * but it would imply approving whatever needs to be in "Manage jenkins > In-process Script Approval"
  * using this library as a "Global Pipeline Library" allows to avoid that

## usage:
- in Jenkinsfile import library like this
  * `@Library('pipeline-logparser@1.0') _`
  * (identifier "pipeline-logparser" is the name of the library set by jenkins administrator in configuration: it may be different on your instance)

- then call one of the 2 main functions
  * `logparser.archiveLogsWithBranchInfo(filename)`
  * `logparser.getLogsWithBranchInfo()`

## documentation

- see 'logparser' documentation in $JOB_URL/pipeline-syntax/ (visible only after the library has been imported once)
- or see [logparser.html](./vars/logparser.html)
