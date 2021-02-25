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

@groovy.transform.Field
def cachedTree = [:]

// **********************
// * INTERNAL FUNCTIONS *
// **********************
@NonCPS
org.jenkinsci.plugins.workflow.job.views.FlowGraphAction _getFlowGraphAction(build) {
    def flowGraph = build.rawBuild.allActions.findAll { it.class == org.jenkinsci.plugins.workflow.job.views.FlowGraphAction }
    assert flowGraph.size() == 1
    return flowGraph[0]
}

@NonCPS
org.jenkinsci.plugins.workflow.graph.FlowNode _getNode(flowGraph, id) {
    def node = flowGraph.nodes.findAll{ it.id == id }
    assert node.size() == 1
    node = node[0]
}

@NonCPS
org.jenkinsci.plugins.workflow.actions.LogAction _getLogAction(node) {
    def logaction = \
        node.actions.findAll {
            it.class.name == 'org.jenkinsci.plugins.workflow.support.actions.LogActionImpl' ||
            it.class.name == 'org.jenkinsci.plugins.workflow.support.actions.LogStorageAction'
        }
    assert logaction.size() == 1 || logaction.size() == 0

    if (logaction.size() == 0) {
        return null
    }
    return logaction[0]
}

@NonCPS
java.util.LinkedHashMap _getNodeTree(build, _flowGraph = null, _node = null, _branches=[], _stages=[]) {
    def key=build.getFullDisplayName()
    if (this.cachedTree.containsKey(key) == false) {
        this.cachedTree[key] = [:]
    }

    def flowGraph = _flowGraph
    if (flowGraph == null) {
        flowGraph = _getFlowGraphAction(build)
    }
    def node = _node
    def name = null
    def stage = false
    def branches = _branches.collect{ it }
    def stages = _stages.collect { it }

    if (node == null || this.cachedTree[key].containsKey(node.id) == false || this.cachedTree[key][node.id].active) {
        // fill in branches and stages lists for children (root branch + named branches/stages only)
        if (node == null) {
            def rootNode = flowGraph.nodes.findAll{ it.enclosingId == null && it.class == org.jenkinsci.plugins.workflow.graph.FlowStartNode }
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

        // add node information in tree
        // get active state first
        def active = node.isActive() == true
        // get children AFTER active state (avoid incomplete list if state was still active)
        def children = flowGraph.nodes.findAll{ it.enclosingId == node.id }.sort{ Integer.parseInt("${it.id}") }
        def logaction = _getLogAction(node)

        // add parent in tree first
        if (this.cachedTree[key].containsKey(node.id) == false) {
            this.cachedTree[key][node.id] = [ \
                id: node.id,
                name: name,
                stage: stage,
                parents: node.allEnclosingIds,
                parent: node.enclosingId,
                children: children.collect{ it.id },
                branches: _branches,
                stages: _stages,
                active: active,
                haslog: logaction != null,
                displayFunctionName: node.displayFunctionName,
                url: node.url
            ]
        } else {
            // node exist in cached tree but was active last time it was updated: refresh its children and status
            this.cachedTree[key][node.id].active = active
            this.cachedTree[key][node.id].children = children.collect{ it.id }
            this.cachedTree[key][node.id].haslog = logaction != null
        }
        // then add children
        children.each{
            _getNodeTree(build, flowGraph, it, branches, stages)
        }
    }
    // else : node was already put in tree while inactive, nothing to update

    return this.cachedTree[key]
}

//*******************************
//* GENERATE URL TO BRANCH LOGS *
//*******************************

// add trailing / and remove any //
@NonCPS
String _cleanRootUrl(String urlIn) {
    def urlOut = urlIn + '/'
    urlOut = urlOut.replaceAll(/([^:])\/\/+/, '$1/')
    return urlOut
}
@NonCPS
java.util.ArrayList getPipelineStepsUrls(build = currentBuild) {
    def tree = _getNodeTree(build)
    def ret = []

    if (this.verbose) {
        print "tree=${tree}"
    }

    // if JENKIN_URL not configured correctly, use placeholder
    def jenkinsUrl = _cleanRootUrl(env.JENKINS_URL ?: '$JENKINS_URL')

    tree.values().each {
        def url = _cleanRootUrl("${jenkinsUrl}${it.url}")
        def log = null
        if (it.haslog) {
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
//      "<nested branch [branch2] [branch21]"
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

    def flowGraph = _getFlowGraphAction(build)
    def tree = _getNodeTree(build, flowGraph)

    if (this.verbose) {
        print "tree=${tree}"
    }

    def keep = [:]

    tree.values().each {
        def branches = it.branches.collect{ it }
        if (options.showStages == false) {
            branches = branches.minus(it.stages)
        }
        branches = branches.collect {
            def id = it
            def item = tree[id]
            assert item != null
            assert item.name != null || item.parent == null
            return item.name
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
                output += "[Pipeline] ${prefix}${it.displayFunctionName}\n"
            }

            if (it.haslog) {
                def node = _getNode(flowGraph, it.id)
                def logaction = _getLogAction(node)
                assert logaction != null

                ByteArrayOutputStream b = new ByteArrayOutputStream()
                if (options.hideVT100) {
                    logaction.logText.writeLogTo(0, b)
                } else {
                    logaction.logText.writeRawLogTo(0, b)
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
            def prefix = null
            if (options.showParents) {
                prefix = branches.reverse().collect{ "[$it]" }.join(' ')
            } else {
                prefix = "[${branches[0]}]"
            }
            output += "<nested branch ${prefix}>\n"
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
