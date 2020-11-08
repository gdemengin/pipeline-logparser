# pipeline-logparser
  
A library to parse and filter logs
* workaround for https://issues.jenkins-ci.org/browse/JENKINS-54304 to help accessing/identifying logs from parallel branches
* implementation of https://stackoverflow.com/a/57351397
  
Content:
  * it provides API to parse logs (from currentBuild or from another run or job) and append them with name of current branch/stage
    * as String for those who need to programatically parse logs
    * or as run artifacts for those who need to archive logs with branch names for later use
  * it provides accessors to 'pipeline step' logs
  * **(new in 2.0)** it provides accessors to 'Blue Ocean' logs urls for parallel branches and stages
  
Compatibility:
  * tested with 2.190.1 & 2.249.3
  * for earlier versions see version 1 (1.4 last tested with 2.73.3, 2.190.1 & 2.249.3)

## Table of contents
- [Documentation](#documentation)
- [Installation](#installation)
- [Know Limitations](#limitations)
- [Change log](#changelog)

## Documentation <a name="documentation"></a>

### import pipeline-logparser library
in Jenkinsfile import library like this
```
@Library('pipeline-logparser@2.0') _
```
_identifier "pipeline-logparser" is the name of the library set by jenkins administrator in instance configuration:_
* _it may be different on your instance_
* _see below [Installation](#installation)_

### use library's functions:

after import the name of the package is logparser:
```
// get logs with branch prefix
def mylog = logparser.getLogsWithBranchInfo()
```

### Detailed Documentation

see online documentation here: [logparser.txt](https://htmlpreview.github.io/?https://github.com/gdemengin/pipeline-logparser/blob/2.0/vars/logparser.txt)  
* _also available in $JOB_URL/pipeline-syntax/globals#logparser_
  * _visible only after the library has been imported once_
  * _requires configuring 'Markup Formater' as 'Safe HTML' in $JENKINS_URL/configureSecurity_

this library provides functions:
* to retrieve logs with branch info (as string or as run artifacts)
* to filter logs from branches
* to retrieve direct urls to logs (Pipeline Steps & Blue Ocean)
  
functionalities:
- **(new in 2.0)** get Blue Ocean links to logs for parallel branches and stages
  * from current run
    ```
    stage ('stage1') {
      parallel (
        branch1: { echo 'in branch1' },
        branch2: { echo 'in branch2' }
      )
    }
    def blueTree = logparser.getBlueOceanUrls()
    ```
    result:
    ```
    blueTree = [
      [
        id:2, name:null, stage:false, parents:[], parent:null,
        url:https://mydomain.com/blue/organizations/jenkins/myjob/detail/myjob/1/pipeline,
        log:https://mydomain.com/blue/rest/organizations/jenkins/pipelines/myjob/runs/1/log/?start=0
      ],
      [
        id:4, name:stage1, stage:true, parents:[2], parent:2,
        url:https://mydomain.com/blue/organizations/jenkins/myjob/detail/myjob/1/pipeline/4,
        log:https://mydomain.com/blue/rest/organizations/jenkins/pipelines/myjob/runs/1/nodes/4/log/?start=0
      ],
      [
        id:7, name:branch1, stage:false, parents:[4, 2], parent:4,
        url:https://mydomain.com/blue/organizations/jenkins/myjob/detail/myjob/1/pipeline/7,
        log:https://mydomain.com/blue/rest/organizations/jenkins/pipelines/myjob/runs/1/nodes/7/log/?start=0
      ],
      [
        id:8, name:branch2, stage:false, parents:[4, 2], parent:4,
        url:https://mydomain.com/blue/organizations/jenkins/myjob/detail/myjob/1/pipeline/8,
        log:https://mydomain.com/blue/rest/organizations/jenkins/pipelines/myjob/runs/1/nodes/8/log/?start=0
      ]
    ]
    ```

  * get Blue Ocean links from another job/run
    ```
    // get RunWrapper for current job last stable run
    // using https://github.com/gdemengin/pipeline-whitelist
    @Library('pipeline-whitelist@2.0.1') _
    def otherBuild = whitelist.getLastStableRunWrapper(whitelist.getJobByName(env.JOB_NAME))

    def blueTree = logparser.getBlueOceanUrls(otherBuild)
    ```

- retrieve logs with branch info

  * add branch prefix [branchName] in front of each line for that branch
    ```
    echo 'not in any branch'
    parallel (
      branch1: { echo 'in branch1' },
      branch2: { echo 'in branch2' }
    )
    print logparser.getLogsWithBranchInfo()
    ```
    result:
    ```
    not in any branch
    [branch1] in branch1
    [branch2] in branch2
    ```

  * show stage name with option `showStages=true`
    ```
    stage ('stage1') {
      echo 'in stage 1'
    }
    print logparser.getLogsWithBranchInfo(showStages: true)
    ```
    result:
    ```
    [stage1] in stage 1
    ```

  * show parent branch name (parent branch first) for nested branches
    ```
    parallel branch2: {
      echo 'in branch2'
      parallel branch21: { echo 'in branch2.branch21' }
    }
    print logparser.getLogsWithBranchInfo()
    ```
    result:
    ```
    [branch2] in branch2
    [branch2] [branch21] in branch2.branch21
    ```

  * hide parent branch name with option `showParents=false`
    ```
    parallel branch2: {
      echo 'in branch2'
      parallel branch21: { echo 'in branch2.branch21' }
    }
    print logparser.getLogsWithBranchInfo(showParents: false)
    ```
    result:
    ```
    [branch2] in branch2
    [branch21] in branch2.branch21
    ```

  * show VT100 markups (hidden by default as they make raw log hard to read) with option `hideVT100=false`
    ```
    // use node to make sure VT100 markups are in the logs
    node {
      def logs = logparser.getLogsWithBranchInfo(hideVT100: false)
      assert logs ==~ /(?s).*\x1B\[8m.*?\x1B\[0m.*/
    }
    ```

  * show Pipeline technical log lines (starting with [Pipeline]) with option `hidePipeline=false`
    ```
    echo 'not in any branch'
    parallel (
      branch1: { echo 'in branch1' },
      branch2: { echo 'in branch2' }
    )
    print logparser.getLogsWithBranchInfo(hidePipeline: false)
    ```
    result:
    ```
    [Pipeline] Start of Pipeline
    [Pipeline] echo
    not in any branch
    [Pipeline] parallel
    [Pipeline] [branch1] { (Branch: branch1)
    [Pipeline] [branch1] echo
    [branch1] in branch1
    [Pipeline] [branch2] { (Branch: branch2)
    [Pipeline] [branch2] echo
    [branch2] in branch2
    [Pipeline] }
    [Pipeline] }
    [Pipeline] // parallel
    ```

- archive logs in job artifacts (without having to allocate a node : same as ArchiveArtifacts but without `node()` scope)
  ```
  echo 'not in any branch'
  parallel (
    branch1: { echo 'in branch1' },
    branch2: { echo 'in branch2' }
  )
  logparser.archiveLogsWithBranchInfo('logs.txt')
  ```
  result: logs.txt in $BUILD_URL/artifact with content:
  ```
  not in any branch
  [branch1] in branch1
  [branch2] in branch2
  ```

- filter branch logs with option `filter=[ list of branches to keep ]`

  * filter by name
    ```
    echo 'not in any branch'
    parallel (
      branch1: { echo 'in branch1' },
      branch2: { echo 'in branch2' }
    )
    print logparser.getLogsWithBranchInfo(filter: [ 'branch1' ])
    ```
    result:
    ```
    [branch1] in branch1
    ```

  * filter multiple branches
    ```
    echo 'not in any branch'
    parallel (
      branch1: { echo 'in branch1' },
      branch2: { echo 'in branch2' },
      branch3: { echo 'in branch3' }
    )
    print logparser.getLogsWithBranchInfo(filter: [ 'branch1', 'branch3' ])
    ```
    result:
    ```
    [branch1] in branch1
    [branch3] in branch3
    ```

  * filter with regular expression
    ```
    echo 'not in any branch'
    parallel (
      branch1: { echo 'in branch1' },
      branch2: { echo 'in branch2' }
    )
    print logparser.getLogsWithBranchInfo(filter: [ '.*2' ])
    ```
    result:
    ```
    [branch2] in branch2
    ```

  * show name of nested branches filtered out
    ```
    parallel branch2: {
      echo 'in branch2'
      parallel branch21: { echo 'in branch2.branch21' }
    }
    print logparser.getLogsWithBranchInfo(filter: [ 'branch2' ])
    ```
    result:
    ```
    [branch2] in branch2
    <nested branch [branch2] [branch21]>
    ```

  * hide name of nested branches filtered out with option `markNestedFiltered=false`
    ```
    parallel branch2: {
      echo 'in branch2'
      parallel branch21: { echo 'in branch2.branch21' }
    }
    print logparser.getLogsWithBranchInfo(filter: [ 'branch2' ], markNestedFiltered: false)
    ```
    result:
    ```
    [branch2] in branch2
    ```

  * filter logs not in any branch
    ```
    echo 'not in any branch'
    parallel (
      branch1: { echo 'in branch1' },
      branch2: { echo 'in branch2' }
    )
    print logparser.getLogsWithBranchInfo(filter: [ null ])
    ```
    result:
    ```
    not in any branch
    <nested branch [branch1]>
    <nested branch [branch2]>
    ```

- get logs from another job/run
    ```
    // get RunWrapper for current job last stable run
    // using https://github.com/gdemengin/pipeline-whitelist
    @Library('pipeline-whitelist@2.0.1') _
    def otherBuild = whitelist.getLastStableRunWrapper(whitelist.getJobByName(env.JOB_NAME))

    def otherBuildLogs = logparser.getLogsWithBranchInfo([:], otherBuild)
    ```

- get pipeline steps tree, with links to logs, and information about parallel branches and stages
  * from current run
    ```
    stage ('stage1') {
      parallel (
        branch1: { echo 'in branch1' },
        branch2: { echo 'in branch2' }
      )
    }
    def stepsTree = logparser.getPipelineStepsUrls()
    ```
    result:
    ```
    stepsTree = [
      [
        id:2, name:null, stage:false, parents:[], parent:null, children:[3, 15],
        url:https://mydomain.com/job/myjob/28/execution/node/2/,
        log:null
      ],
      [
        id:3, name:null, stage:false, parents:[2], parent:2, children:[4, 14],
        url:https://mydomain.com/job/myjob/28/execution/node/3/,
        log:https://mydomain.com/job/myjob/28/execution/node/3/log
      ],
      [
        id:4, name:stage1, stage:true, parents:[3, 2], parent:3, children:[5, 13],
        url:https://mydomain.com/job/myjob/28/execution/node/4/,
        log:null
      ],
      [
        id:5, name:null, stage:false, parents:[4, 3, 2], parent:4, children:[7, 8, 10, 12],
        url:https://mydomain.com/job/myjob/28/execution/node/5/,
        log:https://mydomain.com/job/myjob/28/execution/node/5/log
      ],
      [
        id:7, name:branch1, stage:false, parents:[5, 4, 3, 2], parent:5, children:[9],
        url:https://mydomain.com/job/myjob/28/execution/node/7/,
        log:null
      ],
      [
        id:9, name:null, stage:false, parents:[7, 5, 4, 3, 2], parent:7, children:[],
        url:https://mydomain.com/job/myjob/28/execution/node/9/,
        log:https://mydomain.com/job/myjob/28/execution/node/9/log
      ],
      [
        id:8, name:branch2, stage:false, parents:[5, 4, 3, 2], parent:5, children:[11],
        url:https://mydomain.com/job/myjob/28/execution/node/8/,
        log:null
      ],
      [
        id:11, name:null, stage:false, parents:[8, 5, 4, 3, 2], parent:8, children:[],
        url:https://mydomain.com/job/myjob/28/execution/node/11/,
        log:https://mydomain.com/job/myjob/28/execution/node/11/log
      ],
      [
        id:10, name:null, stage:false, parents:[5, 4, 3, 2], parent:5, children:[],
        url:https://mydomain.com/job/myjob/28/execution/node/10/,
        log:null
      ],
      [
        id:12, name:null, stage:false, parents:[5, 4, 3, 2], parent:5, children:[],
        url:https://mydomain.com/job/myjob/28/execution/node/12/,
        log:null
      ],
      [
        id:13, name:null, stage:false, parents:[4, 3, 2], parent:4, children:[],
        url:https://mydomain.com/job/myjob/28/execution/node/13/,
        log:null
      ],
      [
        id:14, name:null, stage:false, parents:[3, 2], parent:3, children:[],
        url:https://mydomain.com/job/myjob/28/execution/node/14/,
        log:null
      ],
      [
        id:15, name:null, stage:false, parents:[2], parent:2, children:[],
        url:https://mydomain.com/job/myjob/28/execution/node/15/,
        log:null
      ]
    ]
    ```

  * get Pipeline Steps links from another job/run
    ```
    // get RunWrapper for current job last stable run
    // using https://github.com/gdemengin/pipeline-whitelist
    @Library('pipeline-whitelist@2.0.1') _
    def otherBuild = whitelist.getLastStableRunWrapper(whitelist.getJobByName(env.JOB_NAME))

    def stepsTree = logparser.getPipelineStepsUrls(otherBuild)
    ```

## Installation <a name="installation"></a>

install the library as a "Global Pipeline Library" in "Manage jenkins > Configure System > Global Pipeline Library" (cf https://jenkins.io/doc/book/pipeline/shared-libraries/)

![Global Pipeline Library Configuration](images/gpl-config.png)

Note:
  * it's also possible to copy the code in a Jenkinsfile and use functions from there
  * but it would imply approving whatever needs to be in "Manage jenkins > In-process Script Approval" (including some unsafe API's)
  * using this library as a "Global Pipeline Library" allows to avoid that (avoid getting access to unsafe API's)


## Known limitations <a name="limitations"></a>

* calls to `logparser.getLogsWithBranchInfo()` may fail (and cause job to fail) when log is too big (millions of lines, hundreds of MB of logs) because of a lack of heap space

* logs of nested stages (stage inside stage) are not correctly handled in Blue Ocean (Blue Ocean limitation)

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

* 1.2 (09/2020)
  - handle logs from stages and add option showStages (default false) to show/filter them

* 1.3 (10/2020)
  - refactor to use objects available in rawbuild API (no more parsing of xml on disk)  
    fixing known parsing limitations (no more need to wait a few seconds before to parse logs)
  - new API to retrieve URLs to logs of branches (Pipeline Steps URLs)
  - ability to parse logs from another run/job

* 1.4 (11/2020)
  - reformat nested branch markups when filtering is used

* 2.0 (11/2020)
  - new API to retrieve Blue Ocean URLs to logs of branches and stages
