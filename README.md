# pipeline-logparser
a library to parse and filter logs
  * implementation of https://stackoverflow.com/a/57351397
  * workaround for https://issues.jenkins-ci.org/browse/JENKINS-54304

## content
it allows
- to add branch prefix [branchName] in front of each line of the logs belonging to a parallel branch

  [Pipeline] Start of Pipeline
  [Pipeline] parallel
  [Pipeline] { (Branch: branch1)
  [Pipeline] { (Branch: branch2)
  [Pipeline] }
  [Pipeline] echo
  *[branch1]* in branch1
  [Pipeline] sleep
  *[branch1]* Sleeping for 1 sec
  [Pipeline] echo
  *[branch2]* in branch2
  [Pipeline] sleep
  *[branch2]* Sleeping for 1 sec

- to filter logs by branchName

  *[branch1]* in branch1
  *[branch1]* Sleeping for 1 sec

- to show name of parent branches (parent branch first) for nested branches

  *[branch2]* [branch21] in branch2.branch21

- to hide VT100 markups from raw logs

- to access descriptors of log and branches internal ids

- to archive logs in job artifacts (without having to allocate a node : same as ArchiveArtifacts but without `node()` scope)

## installation

it is meant to be used as a "Global Pipeline Library"
- configured by jenkins administrator ("Manage jenkins > Configure System > Global Pipeline Library")
- cf https://jenkins.io/doc/book/pipeline/shared-libraries/

![Global Pipeline Library Configuration](images/gpl-config.png)

Note:
  * it's also possible to copy the code in a Jenkinsfile and use functions from there
  * but it would imply approving whatever needs to be in "Manage jenkins > In-process Script Approval"
  * using this library as a "Global Pipeline Library" allows to avoid that

## usage:

### import logparser library
in Jenkinsfile import library like this
```
@Library('pipeline-logparser@1.0') _
```
  * identifier "pipeline-logparser" is the name of the library set by jenkins administrator in configuration: it may be different on your instance

### use logparser functions:

* `archiveLogsWithBranchInfo(String name, java.util.LinkedHashMap options = [:])`

  archive logs (with branch information) in run artifacts

* `String getLogsWithBranchInfo(java.util.LinkedHashMap options = [:])`

  get logs with branch information

* available options:
  * `filter = null` : name of the branch to filter

  * `showParents = true` : show name of parent branches

    example:  
    *[branch2]* [branch21] in branch21 nested in branch2

  * `markNestedFiltered = true` : add name of nested branches filtered out

    example:  
    [ filtered 315 bytes of logs for nested branches: branch2.branch21 branch2.branch22 ] (...)

  * `hideVT100 = true` : hide the VT100 markups in raw logs

    cf https://www.codesd.com/item/how-to-delete-jenkins-console-log-annotations.html
    cf https://issues.jenkins-ci.org/browse/JENKINS-48344

* examples:
  * archive full logs as $JOB_URL/&lt;runId&gt;/artifacts/consoleText.txt:

    ```logparser.archiveLogsWithBranchInfo('consoleText.txt')```

  * get full logs:

    ```String logs = logparser.getLogsWithBranchInfo()```

  * get logs for branch2 only:

    ```String logsBranch2 = logparser.getLogsWithBranchInfo(filter: 'branch2')```

  * get logs for branch2 without parents or nested branches markups:

    ```String logsBranch2 = logparser.getLogsWithBranchInfo(
           filter: 'branch2',
           markNestedFiltered:false,
           showParents:false
       )```

  * archive logs for branch2 without parents or nested branches markups:

    ```logparser.archiveLogsWithBranchInfo(
           'logsBranch2.txt',
           [
               filter: 'branch2',
               markNestedFiltered: false,
               showParents: false
           ]
       )```

  * get full logs with VT100 markups:

    ```String logs = logparser.getLogsWithBranchInfo(hideVT100: false)```

