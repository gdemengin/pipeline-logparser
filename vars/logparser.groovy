// ===================================
// = logparser for Jenkins pipelines =
// ===================================
// a library to parse and filter logs

// *******************
// * SCRIPT VARIABLE *
// *******************
@groovy.transform.Field
def verbose = false

@NonCPS
def setVerbose(v) {
    this.verbose = v
}

// **********************
// * INTERNAL FUNCTIONS *
// **********************

@NonCPS
java.util.ArrayList _getNodeTree(build = currentBuild, _node = null, _branches=[], _stages=[]) {
    def tree = []

    def flowGraph = build.rawBuild.allActions.findAll { it.class == org.jenkinsci.plugins.workflow.job.views.FlowGraphAction }
    assert flowGraph.size() == 1
    flowGraph = flowGraph[0]

    def node = _node
    def name = null
    def stage = false
    def branches = _branches.collect{ it }
    def stages = _stages.collect { it }

    if (node == null) {
        def rootNode = flowGraph.nodes.findAll{ it.enclosingId == null }
        assert rootNode.size() == 1
        node = rootNode[0]
        branches += [ node.id ]
    } else if (node.class == org.jenkinsci.plugins.workflow.cps.nodes.StepStartNode) {
        if (node.descriptor instanceof org.jenkinsci.plugins.workflow.cps.steps.ParallelStep$DescriptorImpl) {
            def labelAction = node.actions.findAll { it.class == org.jenkinsci.plugins.workflow.cps.steps.ParallelStepExecution$ParallelLabelAction }
            assert labelAction.size() == 1 || labelAction.size() == 0
            if (labelAction.size() == 1) {
                name = labelAction[0].threadName
                branches.add(0, node.id)
            }
        } else if (node.descriptor instanceof org.jenkinsci.plugins.workflow.support.steps.StageStep$DescriptorImpl) {
            def labelAction = node.actions.findAll { it.class == org.jenkinsci.plugins.workflow.actions.LabelAction }
            assert labelAction.size() == 1 || labelAction.size() == 0
            if (labelAction.size() == 1) {
                name = labelAction[0].displayName
                stage = true
                branches.add(0, node.id)
                stages.add(0, node.id)
            }
        }
    }

    def children = flowGraph.nodes.findAll{ it.enclosingId == node.id }.sort{ Integer.parseInt("${it.id}") }
    tree += [ [ id: node.id, node: node, name: name, stage: stage, parents: node.allEnclosingIds, parent: node.enclosingId, children: children.collect{ it.id }, branches: _branches, stages: _stages ] ]

    children.each{ tree += _getNodeTree(build, it, branches, stages) }
    return tree
}

//*******************************
//* GENERATE URL TO BRANCH LOGS *
//*******************************
@NonCPS
java.util.ArrayList getPipelineStepsUrls(build = currentBuild) {
    def tree = _getNodeTree(build)
    def ret = []

    if (this.verbose) {
        print "tree=${tree}"
    }

    tree.each {
        def url = "${env.JENKINS_URL ?: '$JENKINS_URL/'}${it.node.url}"
        def log = null
        if (
            it.node.actions.count {
                it.class.name == 'org.jenkinsci.plugins.workflow.support.actions.LogActionImpl' ||
                it.class.name == 'org.jenkinsci.plugins.workflow.support.actions.LogStorageAction'
            } > 0
        ) {
            log = "${url}log"
        }
        ret += [ [ id: it.id, name: it.name, stage: it.stage, parents: it.parents, parent: it.parent, children: it.children, url: url, log: log ] ]
    }

    if (this.verbose) {
        print "PipelineStepsUrls=${ret}"
    }
    return ret
}

//***************************
//* LOG FILTERING & EDITING *
//***************************

// return log file with BranchInformation
// - return logs only for one branch if filterBranchName not null (default null)
// - with parent information for nested branches if options.showParents is true (default)
//   example:
//      if true: "[branch2] [branch21] log from branch21 nested in branch2"
//      if false "[branch21] log from branch21 nested in branch2"
// - with a marker showing nested branches if options.markNestedFiltered is true (default) and if filterBranchName is not null
//   example:
//      "<nested branch [branch2.branch21]"
// - without VT100 markups if options.hideVT100 is true (default)
// - without Pipeline technical logs if options.hidePipeline is true (default)
// - with stage information if showStage is true (default false)
//
// cf https://stackoverflow.com/questions/38304403/jenkins-pipeline-how-to-get-logs-from-parallel-builds
// cf https://stackoverflow.com/a/57351397
// (workaround for https://issues.jenkins-ci.org/browse/JENKINS-54304)
@NonCPS
String getLogsWithBranchInfo(java.util.LinkedHashMap options = [:], build = currentBuild)
{
    // return value
    def output = ''

    // 1/ parse options
    def defaultOptions = [ filter: [], showParents: true, showStages: false, markNestedFiltered: true, hidePipeline: true, hideVT100: true ]
    // merge 2 maps with priority to options values
    options = defaultOptions.plus(options)
    options.keySet().each{ assert it in ['filter', 'showParents', 'showStages', 'markNestedFiltered', 'hidePipeline', 'hideVT100'], "invalid option $it" }
    // make sure there is no type mismatch when comparing elements of options.filter
    options.filter = options.filter.collect{ it != null ? it.toString() : null }

    /* TODO: option to show logs before start of pipeline
    if (options.filter.size() == 0 || null in options.filter) {
        def b = new ByteArrayOutputStream()
        def s = new StreamTaskListener(b, Charset.forName('UTF-8'))
        build.rawBuild.allActions.findAll{ it.class == hudson.model.CauseAction }.each{ it.causes.each { it.print(s) } }
        if (options.hideVT100) {
            output += b.toString().replaceAll(/\x1B\[8m.*?\x1B\[0m/, '')
        } else {
            output += b.toString()
        }
    }
    */

    def tree = _getNodeTree(build)

    if (this.verbose) {
        print "tree=${tree}"
    }

    def keep = [:]

    tree.each {
        def branches = it.branches.collect{ it }
        if (options.showStages == false) {
            branches = branches.minus(it.stages)
        }
        branches = branches.collect {
            def id = it
            def item = tree.findAll {
                it.id == id
            }
            assert item.size() == 1
            assert item[0].name != null || item[0].parent == null
            return item[0].name
        }.findAll{ it != null }
        if (it.name != null) {
            if (it.stage == false || options.showStages == true) {
                branches.add(0, it.name)
            }
        }

        keep."${it.id}" = \
            options.filter.size() == 0 ||
            (branches.size() == 0 && null in options.filter) ||
            (branches.size() > 0 && options.filter.count{ it != null && branches[0] ==~ /^${it}$/ } > 0)

        if (keep."${it.id}") {
            // no filtering or kept branch: keep logs

            def prefix = ''
            if (branches.size() > 0) {
                if (options.showParents) {
                     prefix = branches.reverse().collect{ "[$it] " }.sum()
                } else {
                     prefix = "[${branches[0]}] "
                }
            }

            if (options.hidePipeline == false) {
                output += "[Pipeline] ${prefix}${it.node.displayFunctionName}\n"
            }

            def logaction = \
                it.node.actions.findAll {
                    it.class.name == 'org.jenkinsci.plugins.workflow.support.actions.LogActionImpl' ||
                    it.class.name == 'org.jenkinsci.plugins.workflow.support.actions.LogStorageAction'
                }
            assert logaction.size() == 1 || logaction.size() == 0
            if (logaction.size() == 1) {
                ByteArrayOutputStream b = new ByteArrayOutputStream()
                if (options.hideVT100) {
                    logaction[0].logText.writeLogTo(0, b)
                } else {
                    logaction[0].logText.writeRawLogTo(0, b)
                }
                if (prefix != '') {
                    def str = b.toString()
                    // split(regex,limit) with negative limit in case of trailing \n\n (0 or positive limit would strip trailing \n and limit the list size)
                    def logList = str.split('\n', -1).collect{ "${prefix}${it}" }
                    if (str.endsWith('\n')) {
                        logList.remove(logList.size() - 1)
                        output += logList.join('\n') + '\n'
                    } else if (str.size() > 0) {
                        output += logList.join('\n')
                    }
                } else {
                     output += b.toString()
                }
            }
        } else if (options.markNestedFiltered && it.name != null && it.parents.findAll { keep."${it}" }.size() > 0) {
            // branch is not kept (not in filter) but one of its parent branch is kept: record it as filtered
            output += "<nested branch [${branches.reverse().join('.')}]>\n"
        }
        // else none of the parent branches is kept, skip this one entirely
    }
    return output
}

//*************
//* ARCHIVING *
//*************

// archive buffer directly on the master (no need to instantiate a node like ArchiveArtifact)
// cf https://github.com/gdemengin/pipeline-whitelist
@NonCPS
void archiveArtifactBuffer(String name, String buffer) {
    def jobRoot = currentBuild.rawBuild.getRootDir()
    def file = new File("${jobRoot}/archive/${name}")

    if (this.verbose) {
        print "logparser: archiving ${file.path}"
    }

    if (! file.parentFile.exists()){
        file.parentFile.mkdirs();
    }
    file.write(buffer)
}

// archive logs with [<branch>] prefix on lines belonging to <branch>
// and filter by branch if filterBranchName not null
// cf https://stackoverflow.com/questions/38304403/jenkins-pipeline-how-to-get-logs-from-parallel-builds
// cf https://stackoverflow.com/a/57351397
// (workaround for https://issues.jenkins-ci.org/browse/JENKINS-54304)
@NonCPS
void archiveLogsWithBranchInfo(String name, java.util.LinkedHashMap options = [:])
{
    archiveArtifactBuffer(name, getLogsWithBranchInfo(options))
}


return this
