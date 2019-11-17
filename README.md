# pipeline-logparser
a library to parse and filter logs
  * implementation of https://stackoverflow.com/a/57351397
  * workaround for https://issues.jenkins-ci.org/browse/JENKINS-54304

## Table of contents
- [Library Content](#content)
- [Documentation](#documentation)
- [Installation](#installation)
- [Know Limitations](#limitations)
- [Change log](#changelog)


## Library Content <a name="content"></a>
this library offer functions to retrieve logs as string or files (as run artifacts), and to parse the logs at the same time  
  
it allows:
- to add branch prefix [branchName] in front of each line of the logs belonging to a parallel branch
  ```
  echo 'not in any branch'
  parallel (
    branch1: { echo 'in branch1' },
    branch2: { echo 'in branch2' }
  )
  ```
  > not in any branch  
  > **[branch1]** in branch1  
  > **[branch2]** in branch2

- to filter logs by branchName

  > **[branch1]** in branch1

- to show the name of parent branches (parent branch first) for nested branches
  ```
  parallel branch2: {
    echo 'in branch2'
    parallel branch21: { echo 'in branch2.branch21' }
  }
  ```
  > **[branch2]** in branch2  
  > **[branch2] [branch21]** in branch2.branch21

- to archive logs in job artifacts (without having to allocate a node : same as ArchiveArtifacts but without `node()` scope)

- to hide or show VT100 markups from logs (which make raw log hard to read)

- to hide or show Pipeline technical log lines (lines starting with [Pipeline] ) from logs
  > [Pipeline] Start of Pipeline  
  > [Pipeline] echo  
  > not in any branch  
  > [Pipeline] parallel  
  > [Pipeline] { (Branch: branch1)  
  > [Pipeline] { (Branch: branch2)  
  > [Pipeline] echo  
  > **[branch1]** in branch1  
  > [Pipeline] }  
  > [Pipeline] echo  
  > **[branch2]** in branch2  
  > [Pipeline] }  
  > [Pipeline] // parallel  
  > [Pipeline] End of Pipeline  
  > Finished: SUCCESS  

- to access descriptors of log and branches internal ids


## Documentation <a name="documentation"></a>

### import pipeline-logparser library
in Jenkinsfile import library like this
```
@Library('pipeline-logparser@stage') _
```
_identifier "pipeline-logparser" is the name of the library set by jenkins administrator in instance configuration:_
* _it may be different on your instance_
* _see below [Installation](#installation)_

### use library's functions:

- the name of the package is logparser:
  ```
  // get logs with branch prefix
  def mylog = logparser.getLogsWithBranchInfo()
  ```

- see complete documentation here: [logparser.txt](https://htmlpreview.github.io/?https://github.com/gdemengin/pipeline-logparser/blob/stage/vars/logparser.txt)  
also available in $JOB_URL/pipeline-syntax/globals#logparser (visible only after the library has been imported once)


## Installation <a name="installation"></a>

install the library as a "Global Pipeline Library" in "Manage jenkins > Configure System > Global Pipeline Library" (cf https://jenkins.io/doc/book/pipeline/shared-libraries/)

![Global Pipeline Library Configuration](images/gpl-config.png)

Note:
  * it's also possible to copy the code in a Jenkinsfile and use functions from there
  * but it would imply approving whatever needs to be in "Manage jenkins > In-process Script Approval" (including some unsafe API's)
  * using this library as a "Global Pipeline Library" allows to avoid that (avoid getting access to unsafe API's)


## Known limitations <a name="limitations"></a>

* parsing may fail (and cause job to fail) when log is too big (millions of lines, hundreds of MB of logs) because of a lack of heap space

* when logparser functions are called the last lines of logs might not be flushed yet and shall not be in the resulting log
  workaround: before to call logparser add these 2 statements
  ```
  // sleep 1s and use echo to flush logs before to call logparser
  sleep 1
  echo ''
  ```
  example:  
  ```
  @Library('pipeline-logparser@stage') _

  parallel(
    branch1: { echo 'in branch1' },
    branch2: { echo 'in branch2' }
  )

  // sleep 1s and use echo to flush logs before to call logparser
  sleep 1
  echo ''

  logparser.archiveLogsWithBranchInfo('console.txt')
  ```
  NB: this might not always be enough

* when hidePipeline is false, the output is not fully equivalent to what we had with job-workflow plugin v2.25 and earlier :
  example:  
  * pipeline code

    ```
    parallel(
      branch1: { echo 'in branch1' },
      branch2: { echo 'in branch2' }
    )
    ```
  * job-workflow plugin v2.25 output:

    > [Pipeline] parallel  
    > [Pipeline] **[branch1]** { (Branch: branch1)  
    > [Pipeline] **[branch2]** { (Branch: branch2)  
    > [Pipeline] **[branch1]** echo  
    > [branch1] in branch1  
    > [Pipeline] **[branch1]** }  
    > [Pipeline] **[branch2]** echo  
    > [branch2] in branch2  
    > [Pipeline] **[branch2]** }  
    > [Pipeline] // parallel  
    > [Pipeline] End of Pipeline

  * output using `logparser.getLogsWithBranchInfo()`:

    > [Pipeline] Start of Pipeline  
    > [Pipeline] parallel  
    > [Pipeline] { (Branch: branch1)  
    > [Pipeline] { (Branch: branch2)  
    > [Pipeline] echo  
    > [branch1] in branch1  
    > [Pipeline] }  
    > [Pipeline] echo  
    > [branch2] in branch2  
      
    So we lose a bit of information: the lines starting with `[Pipeline]` and which belonged to a specific branch like `[Pipeline] [branch2] echo`  
    It might not be the most important information but sometimes it is useful to know which branch it belongs to

## Change log <a name="changelog"></a>

* 1.0 (09/2019)
  - API to get edited logs with branchName (with nested branches, without VT100 markups, etc ...)
  - API to filter logs by branch name (get logs of a specific branch)
  - API to parse logs and retrieve maps of id/offsets/branchname

* 1.0.1 (11/2019)
  - fix parsing issues (when there is only one branch and when logs of 2 branches are mixed without pipeline technical logs in between)

* 1.1 (03/2020)
  - ability to set branch name to filter using regular expression
  - ability to filter more than one branch (API change : option filter is now a list)
  - ability to filter main thread (using filter = [null])
  - new option hidePipeline to filter Pipeline technical logs (default value true to hide them)
  - fix parsing issues with old version of workflow-job plugin

* stage branch (??/2020)
  - handle logs from stages and add option to show/filter them
