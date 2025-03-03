# pipeline-logparser

[![.github/workflows/main.yml](https://github.com/gdemengin/pipeline-logparser/actions/workflows/main.yml/badge.svg)](https://github.com/gdemengin/pipeline-logparser/actions/workflows/main.yml)
[![.github/workflows/update-version.yml](https://github.com/gdemengin/pipeline-logparser/actions/workflows/update-version.yml/badge.svg)](https://github.com/gdemengin/pipeline-logparser/actions/workflows/update-version.yml)
  
A library to parse and filter logs
* workaround for https://issues.jenkins-ci.org/browse/JENKINS-54304 to help accessing/identifying logs from parallel branches
* implementation of https://stackoverflow.com/a/57351397
  
Content:
  * it provides API to parse logs (from currentBuild or from another run or job) and append them with name of current branch/stage
    * as String for those who need to programatically parse logs
    * or as run artifacts for those who need to archive logs with branch names for later use
  * it provides accessors to 'pipeline step' logs
  * it provides accessors to 'Blue Ocean' logs urls for parallel branches and stages
  
Tested with:

[![test/jenkins-lts/plugins.txt](https://img.shields.io/badge/jenkins-lts-blue.svg)](test/jenkins-lts/plugins.txt)
[![test/jenkins-last/versions.txt](https://img.shields.io/badge/jenkins-2.492.1-blue.svg)](test/jenkins-last/versions.txt)
[![test/jenkins-2.190.1/versions.txt](https://img.shields.io/badge/jenkins-2.190.1-blue.svg)](test/jenkins-2.190.1/versions.txt)

## Table of contents
- [Documentation](#documentation)
- [Installation](#installation)
- [Know Limitations](#limitations)
- [How to test](#tests)
- [Change log](#changelog)

## Documentation <a name="documentation"></a>

### import pipeline-logparser library
in Jenkinsfile import library like this
```
@Library('pipeline-logparser@3.2.1') _
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

see online documentation here: [logparser.txt](https://htmlpreview.github.io/?https://github.com/gdemengin/pipeline-logparser/blob/3.2.1/vars/logparser.txt)  
* _also available in $JOB_URL/pipeline-syntax/globals#logparser_
  * _visible only after the library has been imported once_
  * _requires configuring 'Markup Formater' as 'Safe HTML' in $JENKINS_URL/configureSecurity_
 
this library provides functions:
* to retrieve logs with branch info (as string or as run artifacts)
* to filter logs from branches
* to retrieve direct urls to logs (Pipeline Steps & Blue Ocean)
  
functionalities:
- get Blue Ocean links to logs for parallel branches and stages
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
    @Library('pipeline-whitelist@4.0') _
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

  * hide stage name with option `showStages=false`
    ```
    stage ('stage1') {
      echo 'in stage 1'
    }
    print logparser.getLogsWithBranchInfo(showStages: false)
    ```
    result:
    ```
    in stage 1
    ```
    or show stage name (default)
    ```
    stage ('stage1') {
      echo 'in stage 1'
    }
    print logparser.getLogsWithBranchInfo()
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

  * show duplicate parent branch name with option `mergeNestedDuplicates=false`
    ```
    parallel branch2: {
      stage('branch2') { echo 'in stage branch2' }
    }
    print logparser.getLogsWithBranchInfo(mergeNestedDuplicates: false)
    ```
    result:
    ```
    [branch2] [branch2] in stage branch2
    ```
    or hide duplicates (default)
    ```
    parallel branch2: {
      stage('branch2') { echo 'in stage branch2' }
    }
    print logparser.getLogsWithBranchInfo()
    ```
    result:
    ```
    [branch2] in stage branch2
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

- write logs directly to a file
  ```
  echo 'not in any branch'
  parallel (
    branch1: { echo 'in branch1' },
    branch2: { echo 'in branch2' }
  )
  node('myhost') {
    logparser.writeLogsWithBranchInfo(env.NODE_NAME, "${pwd()}/logs.txt")
  }
  ```
  result: log.txt in workspace on node 'myhost' with content:
  ```
  not in any branch
  [branch1] in branch1
  [branch2] in branch2
  Running on myhost in /home/jenkins/workspace/test-pipeline
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

  * filter branch using list of parent name(s)
    ```
    parallel branch1: {
      echo 'in branch1'
      parallel branch11: {
        echo 'in branch1.branch11'
        parallel branchA: {
          echo 'in branch1.branch11.branchA'
        }
      }
    }, branch2: {
      echo 'in branch2'
      parallel branch21: {
        echo 'in branch2.branch21'
        parallel branchA: {
          echo 'in branch2.branch21.branchA'
        }
      }
    }
    print logparser.getLogsWithBranchInfo(filter: [ [ 'branch2', 'branch21', 'branchA' ] ])
    ```
    result:
    ```
    [branch2] [branch21] [branchA] in branch2.branch21.branchA
    ```

  * filter branch using the immediate parent name(s)
    ```
    parallel branch1: {
      echo 'in branch1'
      parallel branch11: {
        echo 'in branch1.branch11'
        parallel branchA: {
          echo 'in branch1.branch11.branchA'
        }
      }
    }, branch2: {
      echo 'in branch2'
      parallel branch21: {
        echo 'in branch2.branch21'
        parallel branchA: {
          echo 'in branch2.branch21.branchA'
        }
      }
    }
    print logparser.getLogsWithBranchInfo(filter: [ [ 'branch21', 'branchA' ] ])
    ```
    result:
    ```
    [branch2] [branch21] [branchA] in branch2.branch21.branchA
    ```

  * filter branch using regular expression on parent name(s)
    ```
    parallel branch1: {
      echo 'in branch1'
      parallel branch11: {
        echo 'in branch1.branch11'
        parallel branchA: {
          echo 'in branch1.branch11.branchA'
        }
      }
    }, branch2: {
      echo 'in branch2'
      parallel branch21: {
        echo 'in branch2.branch21'
        parallel branchA: {
          echo 'in branch2.branch21.branchA'
        }
      }
    }
    print logparser.getLogsWithBranchInfo(filter: [ [ 'branch2.*', '.*A' ] ])
    ```
    result:
    ```
    [branch2] [branch21] [branchA] in branch2.branch21.branchA
    ```

- get logs from another job/run
    ```
    // get RunWrapper for current job last stable run
    // using https://github.com/gdemengin/pipeline-whitelist
    @Library('pipeline-whitelist@4.0') _
    def otherBuild = whitelist.getLastStableRunWrapper(whitelist.getJobByName(env.JOB_NAME))

    def otherBuildLogs = logparser.getLogsWithBranchInfo([:], otherBuild)
    ```

- get pipeline steps tree, with links to logs, and information about parallel branches and stages
  * from current run
    ```
    stage ('stage1') {
      parallel (
        branch1: { echo 'in branch1' },
        branch2: {
          node('linux') {
            echo 'in branch2'
          }
        }
      )
    }
    def stepsTree = logparser.getPipelineStepsUrls()
    ```
    result:
    ```
    stepsTree = [
      {
        id=2, name=null, stage=false, parents=[], parent=null, children=[3, 19],
        url=http://ci.jenkins.internal:8080/job/testFolder/job/testNestedFolder/job/testPipeline/7/execution/node/2/,
        log=null, label=null, host=null
      },
      {
        id=3, name=null, stage=false, parents=[2], parent=2, children=[4, 18],
        url=http://ci.jenkins.internal:8080/job/testFolder/job/testNestedFolder/job/testPipeline/7/execution/node/3/,
        log=http://ci.jenkins.internal:8080/job/testFolder/job/testNestedFolder/job/testPipeline/7/execution/node/3/log, label=null, host=null
      },
      {
        id=4, name=stage1, stage=true, parents=[3, 2], parent=3, children=[5, 17],
        url=http://ci.jenkins.internal:8080/job/testFolder/job/testNestedFolder/job/testPipeline/7/execution/node/4/,
        log=null, label=null, host=null
      },
      {
        id=5, name=null, stage=false, parents=[4, 3, 2], parent=4, children=[7, 8, 10, 16],
        url=http://ci.jenkins.internal:8080/job/testFolder/job/testNestedFolder/job/testPipeline/7/execution/node/5/,
        log=http://ci.jenkins.internal:8080/job/testFolder/job/testNestedFolder/job/testPipeline/7/execution/node/5/log, label=null, host=null
      },
      {
        id=7, name=branch1, stage=false, parents=[5, 4, 3, 2], parent=5, children=[9],
        url=http://ci.jenkins.internal:8080/job/testFolder/job/testNestedFolder/job/testPipeline/7/execution/node/7/,
        log=null, label=null, host=null
      },
      {
         id=9, name=null, stage=false, parents=[7, 5, 4, 3, 2], parent=7, children=[],
         url=http://ci.jenkins.internal:8080/job/testFolder/job/testNestedFolder/job/testPipeline/7/execution/node/9/,
         log=http://ci.jenkins.internal:8080/job/testFolder/job/testNestedFolder/job/testPipeline/7/execution/node/9/log, label=null, host=null
      },
      {
         id=8, name=branch2, stage=false, parents=[5, 4, 3, 2], parent=5, children=[11, 15],
         url=http://ci.jenkins.internal:8080/job/testFolder/job/testNestedFolder/job/testPipeline/7/execution/node/8/,
         log=null, label=null, host=null
      },
      {
         id=11, name=null, stage=false, parents=[8, 5, 4, 3, 2], parent=8, children=[12, 14],
         url=http://ci.jenkins.internal:8080/job/testFolder/job/testNestedFolder/job/testPipeline/7/execution/node/11/,
         log=http://ci.jenkins.internal:8080/job/testFolder/job/testNestedFolder/job/testPipeline/7/execution/node/11/log, label=linux, host=linux-12345
      },
      {
         id=12, name=null, stage=false, parents=[11, 8, 5, 4, 3, 2], parent=11, children=[13],
         url=http://ci.jenkins.internal:8080/job/testFolder/job/testNestedFolder/job/testPipeline/7/execution/node/12/,
         log=null, label=null, host=null
      },
      {
         id=13, name=null, stage=false, parents=[12, 11, 8, 5, 4, 3, 2], parent=12, children=[],
         url=http://ci.jenkins.internal:8080/job/testFolder/job/testNestedFolder/job/testPipeline/7/execution/node/13/,
         log=http://ci.jenkins.internal:8080/job/testFolder/job/testNestedFolder/job/testPipeline/7/execution/node/13/log, label=null, host=null
      },
      {
         id=14, name=null, stage=false, parents=[11, 8, 5, 4, 3, 2], parent=11, children=[],
         url=http://ci.jenkins.internal:8080/job/testFolder/job/testNestedFolder/job/testPipeline/7/execution/node/14/,
         log=null, label=null, host=null
      },
      {
         id=15, name=null, stage=false, parents=[8, 5, 4, 3, 2], parent=8, children=[],
         url=http://ci.jenkins.internal:8080/job/testFolder/job/testNestedFolder/job/testPipeline/7/execution/node/15/,
         log=null, label=null, host=null
      },
      {
         id=10, name=null, stage=false, parents=[5, 4, 3, 2], parent=5, children=[],
         url=http://ci.jenkins.internal:8080/job/testFolder/job/testNestedFolder/job/testPipeline/7/execution/node/10/,
         log=null, label=null, host=null
      },
      {
         id=16, name=null, stage=false, parents=[5, 4, 3, 2], parent=5, children=[],
         url=http://ci.jenkins.internal:8080/job/testFolder/job/testNestedFolder/job/testPipeline/7/execution/node/16/,
         log=null, label=null, host=null
      },
      {
         id=17, name=null, stage=false, parents=[4, 3, 2], parent=4, children=[],
         url=http://ci.jenkins.internal:8080/job/testFolder/job/testNestedFolder/job/testPipeline/7/execution/node/17/,
         log=null, label=null, host=null
      },
      {
         id=18, name=null, stage=false, parents=[3, 2], parent=3, children=[],
         url=http://ci.jenkins.internal:8080/job/testFolder/job/testNestedFolder/job/testPipeline/7/execution/node/18/,
         log=null, label=null, host=null
      },
      {
         id=19, name=null, stage=false, parents=[2], parent=2, children=[],
         url=http://ci.jenkins.internal:8080/job/testFolder/job/testNestedFolder/job/testPipeline/7/execution/node/19/,
         log=null, label=null, host=null
      }
    ]
    ```

  * get Pipeline Steps links from another job/run
    ```
    // get RunWrapper for current job last stable run
    // using https://github.com/gdemengin/pipeline-whitelist
    @Library('pipeline-whitelist@4.0') _
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
workarround: use `logparser.writeLogswithBranchInfo` to write logs directly in a file (in node workspace) or `logparser.archiveLogsWithBranchInfo()` to write them directly in run artifacts

* logs of nested stages (stage inside stage) are not correctly handled in Blue Ocean (Blue Ocean limitation)

* if 2 steps or stages have the sane name the logs will be combined in getLogsWithBranchInfo output (one may use filter option to filter on the parent's name in order to separate them)

## How to test <a name="tests"></a>

* to test on jenkins lts
  ```
  ./test/jenkins-lts/run.sh
  ```
  it shall:
  - start jenkins
  - create local Global Pipeline Library from local branch (1)
  - run Jenkinsfile from local branch (1)
  - stop jenkins
  - get result

  (1) CAUTION: changes to test must be commited in the local branch

* to test on jenkins last known good version
  ```
  ./test/jenkins-last/run.sh
  ```

* to keep instance running after the test and make it accessible on http://localhost:8080 (jenkins/jenkins)
  ```
  ./test/jenkins-lts/run.sh -keepalive -port 8080
  ```

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

* 1.4.1 (02/2021)
  - optimize workflow steps parsing (avoid slowdown with large, and/or massively parallel, workflows)

* 2.0 (11/2020)
  - new API to retrieve Blue Ocean URLs to logs of branches and stages

* 2.0.1 (02/2021)
  - optimize workflow steps parsing (avoid slowdown with large, and/or massively parallel, workflows)

* 3.0 (01/2022)
  - getLogsWithBranchInfo() now shows stages by default (option showStages now true by default)
  - add ability to remove duplicates branch name (when parent and child branch have the same name) in getLogsWithBranchInfo()
    use option mergeNestedDuplicates=false to keep duplicates (default true)
  - add ability to filter using parent(s) branch name(s) (option filter can be a list of branch names, with parents first)
  - new API getBranches() to get all branch names (with parent names)
  - add label in getPipelineStepsUrl for node/agent steps

* 3.1 (02/2022)
  - fix parsing when no label is used in node/agent steps
  - add host in getPipelineStepsUrl for node/agent steps

* 3.1.1 (04/2022)
  - speed optimisation

* 3.1.2 (04/2022)
  - fix issue when parsing logs while some node step is still searching for a host to allocate

* 3.1.3 (06/2022)
  - fix parsing of completed jobs #16

* 3.2 (08/2022)
  - add function to write directly to a file #21

* 3.2.1 (03/2024)
  - fix installation when blueocean plugin is missing
