// ===================================
// = logparser for Jenkins pipelines =
// ===================================
// a library to parse and filter logs

import org.apache.commons.io.IOUtils

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

// expose flowGraphAction as node id map
// with list of children cached to speed-up
// and active status to avoid inconsistencies with children list
@NonCPS
java.util.LinkedHashMap _getFlowGraphMap(build) {
    def flowGraph = build.rawBuild.allActions.findAll { it.class == org.jenkinsci.plugins.workflow.job.views.FlowGraphAction }
    assert flowGraph.size() == 1
    flowGraph = flowGraph[0]

    // init map with copy of active status
    // to avoid incomplete list of children if state is changing from active to inactive
    // (once inactive, nodes & their children are not updated if cachedTree is updated)
    def flowGraphMap = flowGraph.nodes.collectEntries {
        [
            (it.id): [
                node: it,
                active: it.active == true,
                children: []
            ]
        ]
    }

    // cache children to speed-up
    // get children AFTER active state
    def start = null
    flowGraph.nodes.each {
        if (it.enclosingId != null) {
            flowGraphMap[it.enclosingId].children.add(it)
            flowGraphMap[it.id] += _getNodeInfos(it)
        }
        else if (it.class != org.jenkinsci.plugins.workflow.graph.FlowEndNode) {
            // https://javadoc.jenkins.io/plugin/workflow-api/org/jenkinsci/plugins/workflow/graph/FlowNode.html#getEnclosingId--
            // only FlowStartNode & FlowEndNode should have null enclosingId
            assert it.class == org.jenkinsci.plugins.workflow.graph.FlowStartNode
            assert start == null
            start = it.id
            flowGraphMap[it.id].isBranch = true
        }
    }

    return [start: start, map: flowGraphMap]
}

@NonCPS
java.util.LinkedHashMap _getNodeInfos(node) {
    def infos = [:]
    if (node.class == org.jenkinsci.plugins.workflow.cps.nodes.StepStartNode) {
        if (node.descriptor instanceof org.jenkinsci.plugins.workflow.cps.steps.ParallelStep$DescriptorImpl) {
            def labelAction = node.actions.findAll { it.class == org.jenkinsci.plugins.workflow.cps.steps.ParallelStepExecution$ParallelLabelAction }
            assert labelAction.size() == 1 || labelAction.size() == 0
            if (labelAction.size() == 1) {
                infos += [ name: labelAction[0].threadName, isBranch: true ]
            }
        } else if (node.descriptor instanceof org.jenkinsci.plugins.workflow.support.steps.StageStep$DescriptorImpl) {
            def labelAction = node.actions.findAll { it.class == org.jenkinsci.plugins.workflow.actions.LabelAction }
            assert labelAction.size() == 1 || labelAction.size() == 0
            if (labelAction.size() == 1) {
                infos += [ name: labelAction[0].displayName, isStage: true, isBranch: true ]
            }
        } else if (node.descriptor instanceof org.jenkinsci.plugins.workflow.support.steps.ExecutorStep$DescriptorImpl && node.displayName=='Allocate node : Start') {
            def argAction = node.actions.findAll { it.class == org.jenkinsci.plugins.workflow.cps.actions.ArgumentsActionImpl }
            assert argAction.size() == 1 || argAction.size() == 0
            // record the label if any
            if (argAction.size() == 1 && argAction[0].unmodifiedArguments) {
                infos += [ label: argAction[0].argumentsInternal.label ]
            }

            def wsAction = node.actions.findAll { it.class == org.jenkinsci.plugins.workflow.support.actions.WorkspaceActionImpl }
            // hostname may be missing if host not yet allocated
            assert wsAction.size() == 1 || wsAction.size() == 0
            // record hostname if any
            if (wsAction.size() == 1) {
                infos += [ hostname: wsAction[0].node ]
            }
        }
    }
    return infos
}

@NonCPS
java.util.LinkedHashMap _getNodeTree(build, _flowGraphMap = null, _node = null) {
    def key=build.getFullDisplayName()
    if (this.cachedTree.containsKey(key) == false) {
        this.cachedTree[key] = [:]
    }

    def flowGraphMap = _flowGraphMap
    if (flowGraphMap == null) {
        flowGraphMap = _getFlowGraphMap(build)
    }

    if (flowGraphMap.map.size() == 0) {
        // pipeline not yet started, or failed before start
        assert _node == null
        return [:]
    }

    def node = _node
    if (node == null) {
        node = flowGraphMap.map[flowGraphMap.start].node
    }

    // add current node to cache if not already there
    // or update it, if it was still active in cache (and possibly incomplete)
    if (this.cachedTree[key].containsKey(node.id) == false || this.cachedTree[key][node.id].active) {
        def children = flowGraphMap.map[node.id].children.sort{ Integer.parseInt("${it.id}") }

        // add parent in tree first
        this.cachedTree[key][node.id] = [ \
            id: node.id,
            name: flowGraphMap.map[node.id].name,
            stage: flowGraphMap.map[node.id].isStage == true,
            parents: node.allEnclosingIds,
            parent: node.enclosingId,
            children: children.collect{ it.id },
            branches: node.allEnclosingIds.findAll{ flowGraphMap.map[it].isBranch },
            stages: node.allEnclosingIds.findAll{ flowGraphMap.map[it].isStage },
            active: flowGraphMap.map[node.id].active == true,
            haslog: _getLogAction(node) != null,
            displayFunctionName: node.displayFunctionName,
            url: node.url,
            label: flowGraphMap.map[node.id].label,
            host: flowGraphMap.map[node.id].hostname
        ]

        // then add children
        children.each{
            _getNodeTree(build, flowGraphMap, it)
        }
    }
    // else : node was already put in tree while inactive, nothing to update

    return this.cachedTree[key]
}


@NonCPS
java.util.ArrayList _getBranches(java.util.LinkedHashMap tree, id, Boolean showStages, Boolean mergeNestedDuplicates) {
    def branches = tree[id].branches.collect{ it }
    if (showStages == false) {
        branches = branches.minus(tree[id].stages)
    }
    branches = branches.collect {
        def item = tree[it]
        assert item != null
        assert item.name != null || item.parent == null
        return item.name
    }.findAll { it != null }

    if (tree[id].name != null) {
        if (tree[id].stage == false || showStages == true) {
            branches.add(0, tree[id].name)
        }
    }

    // remove consecutive duplicates
    // this would happen if 2 nested parallel steps or stages have the same name
    // which is exactly what happens in declarative matrix (parallel step xxx contain stage xxx)
    if (mergeNestedDuplicates) {
        def i = 0
        branches = branches.findAll {
            i++ == 0 || branches[i - 2] != it
        }
    }
    return branches
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
java.util.ArrayList getBlueOceanUrls(build = currentBuild) {
    // if JENKIN_URL not configured correctly, use placeholder
    def jenkinsUrl = _cleanRootUrl(env.JENKINS_URL ?: '$JENKINS_URL')

    def rootUrl = null
    build.rawBuild.allActions.findAll { it.class == io.jenkins.blueocean.service.embedded.BlueOceanUrlAction }.each {
        rootUrl = _cleanRootUrl(jenkinsUrl + it.blueOceanUrlObject.url)
    }
    assert rootUrl != null

    try {
        // TODO : find a better way to do get the rest url for this build ...
        def blueProvider = new io.jenkins.blueocean.service.embedded.BlueOceanRootAction.BlueOceanUIProviderImpl()
        def buildenv = build.rawBuild.getEnvironment()
        def restUrl = _cleanRootUrl("${jenkinsUrl}${blueProvider.getUrlBasePrefix()}/rest${blueProvider.getLandingPagePath()}${buildenv.JOB_NAME.replace('/','/pipelines/')}/runs/${buildenv.BUILD_NUMBER}")

        def tree = _getNodeTree(build)
        def ret = []
    
        if (this.verbose) {
            print "rootUrl=${rootUrl}"
            print "restUrl=${restUrl}"
            print "tree=${tree}"
        }
    
        tree.values().findAll{ it.parent == null || it.name != null }.each {
            def url = "${rootUrl}pipeline/${it.id}"
            def log = "${restUrl}nodes/${it.id}/log/?start=0"
            if (it.parent == null) {
                url = "${rootUrl}pipeline"
                log = "${restUrl}log/?start=0"
            }
            // if more than one stage blue ocean urls are invalid
            def parent = it.branches.size() > 0 ? it.branches[0] : null
            ret += [ [ id: it.id, name: it.name, stage: it.stage, parents: it.branches, parent: parent, url: url, log: log ] ]
        }
    
        if (this.verbose) {
            print "BlueOceanUrls=${ret}"
        }
        return ret
    } catch (ClassNotFoundException e) {
       return []
    }
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
        ret += [ [ id: it.id, name: it.name, stage: it.stage, parents: it.parents, parent: it.parent, children: it.children, url: url, log: log, label: it.label, host: it.host ] ]
    }

    if (this.verbose) {
        print "PipelineStepsUrls=${ret}"
    }
    return ret
}

//***************************
//* LOG FILTERING & EDITING *
//***************************

@NonCPS
java.util.LinkedHashMap _parseOptions(java.util.LinkedHashMap options)
{
    def defaultOptions = [
        filter: [],
        showParents: true,
        showStages: true,
        markNestedFiltered: true,
        hidePipeline: true,
        hideVT100: true,
        mergeNestedDuplicates: true
    ]
    // merge 2 maps with priority to options values
    def new_options = defaultOptions.plus(options)
    new_options.keySet().each{
        assert it in [
            'filter',
            'showParents',
            'showStages',
            'markNestedFiltered',
            'mergeNestedDuplicates',
            'hidePipeline',
            'hideVT100'
        ], "invalid option $it"
    }

    return new_options
}

@NonCPS
Boolean _keepBranches(java.util.ArrayList branches, java.util.ArrayList filter) {
    return \
        filter.size() == 0 ||
        (branches.size() == 0 && null in filter) ||
        (branches.size() > 0 && filter.count{ it != null && it in CharSequence && branches[0] ==~ /^${it.toString()}$/ } > 0) ||
        (branches.size() > 0 && filter.count{
            if (it != null && it in Collection && branches.size() >= it.size()) {
                def index = 0
                it.count { pattern ->
                    branches[it.size() - index++ - 1] ==~ /^${pattern.toString()}$/
                } == it.size()
            }
        } > 0)
}

@NonCPS
void _appendToOutput(java.io.OutputStream output, String logs, String prefix = '') {
    if (logs.size() == 0) {
        return
    }

    if (prefix != '') {
        // split(regex,limit) with negative limit in case of trailing \n\n
        // (0 or positive limit would strip trailing \n and/or limit the list size)
        def logList = logs.split('\n', -1).collect{ "${prefix}${it}" }
        if (logs.endsWith('\n')) {
            // no prefix for trailing \n
            logList.remove(logList.size() -1)
            IOUtils.write(logList.join('\n') + '\n', output, 'UTF-8')
        }
        else {
            IOUtils.write(logList.join('\n'), output, 'UTF-8')
        }
    } else {
        IOUtils.write(logs, output, 'UTF-8')
    }
}

@NonCPS
void _getLogsWithBranchInfo(
    java.io.OutputStream output,
    java.util.LinkedHashMap options,
    build
) {
    // 1/ parse options
    def opt = _parseOptions(options)

    /* TODO: option to show logs before start of pipeline
    if (opt.filter.size() == 0 || null in opt.filter) {
        def b = new ByteArrayOutputStream()
        def s = new StreamTaskListener(b, Charset.forName('UTF-8'))
        build.rawBuild.allActions.findAll{ it.class == hudson.model.CauseAction }.each{ it.causes.each { it.print(s) } }
        if (opt.hideVT100) {
            _appendToOutput(output, b.toString().replaceAll(/\x1B\[8m.*?\x1B\[0m/, ''))
        } else {
            _appendToOutput(output, b.toString())
        }
    }
    */

    def flowGraphMap = _getFlowGraphMap(build)
    def tree = _getNodeTree(build, flowGraphMap)

    if (this.verbose) {
        print "tree=${tree}"
    }

    def keep = [:]

    tree.values().each {
        def branches = _getBranches(tree, it.id, opt.showStages, opt.mergeNestedDuplicates)

        keep."${it.id}" = _keepBranches(branches, opt.filter)

        if (keep."${it.id}") {
            // no filtering or kept branch: keep logs

            def prefix = ''
            if (branches.size() > 0) {
                if (opt.showParents) {
                     prefix = branches.reverse().collect{ "[$it] " }.sum()
                } else {
                     prefix = "[${branches[0]}] "
                }
            }

            if (opt.hidePipeline == false) {
                _appendToOutput(output, "[Pipeline] ${prefix}${it.displayFunctionName}\n")
            }

            if (it.haslog) {
                def node = flowGraphMap.map[it.id].node
                def logaction = _getLogAction(node)
                assert logaction != null

                ByteArrayOutputStream b = new ByteArrayOutputStream()
                if (opt.hideVT100) {
                    logaction.logText.writeLogTo(0, b)
                } else {
                    logaction.logText.writeRawLogTo(0, b)
                }
                _appendToOutput(output, b.toString(), prefix)
            }
        } else if (opt.markNestedFiltered && it.name != null && it.parents.findAll { keep."${it}" }.size() > 0) {
            def showNestedMarker = true
            if (opt.mergeNestedDuplicates) {
                def branchesWithDuplicates = _getBranches(tree, it.id, opt.showStages, false)
                if (branchesWithDuplicates.size() > 1 && branchesWithDuplicates[1] == it.name) {
                    // this is already a duplicate branch merged into its parent : marker was already put for parent branch
                    showNestedMarker = false
                }
            }
            if (showNestedMarker) {
                // branch is not kept (not in filter) but one of its parent branch is kept: record it as filtered
                def prefix = null
                if (opt.showParents) {
                    prefix = branches.reverse().collect{ "[$it]" }.join(' ')
                } else {
                    prefix = "[${branches[0]}]"
                }
                _appendToOutput(output, "<nested branch ${prefix}>\n")
            }
        }
        // else none of the parent branches is kept, skip this one entirely
    }
}

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
// - with stage information if showStage is true (default true)
// - with duplicate branch names removed if mergeNestedDuplicates is true (default true)
//
// cf https://stackoverflow.com/questions/38304403/jenkins-pipeline-how-to-get-logs-from-parallel-builds
// cf https://stackoverflow.com/a/57351397
// (workaround for https://issues.jenkins-ci.org/browse/JENKINS-54304)
@NonCPS
String getLogsWithBranchInfo(java.util.LinkedHashMap options = [:], build = currentBuild)
{
    def output = new ByteArrayOutputStream()
    _getLogsWithBranchInfo(output, options, build)
    return output
}

// same as getLogsWithBranchInfo but write directly to artifact of current run
@NonCPS
void archiveLogsWithBranchInfo(String name, java.util.LinkedHashMap options = [:])
{
    def jobRoot = currentBuild.rawBuild.getRootDir()
    def file = new File("${jobRoot}/archive/${name}")

    if (this.verbose) {
        print "logparser: archiving ${file.path}"
    }

    if (! file.parentFile.exists()){
        file.parentFile.mkdirs();
    }
    writeLogsWithBranchInfo(new hudson.FilePath(file), options)
}

// same as getLogsWithBranchInfo but write directly to file
@NonCPS
void writeLogsWithBranchInfo(hudson.FilePath filePath, java.util.LinkedHashMap options = [:], build = currentBuild)
{
    def output = filePath.write()
    assert output instanceof java.io.OutputStream
    _getLogsWithBranchInfo(output, options, build)
}

// same but creating the filePath object
// keep it CPS to access jenkins instance
void writeLogsWithBranchInfo(String node, String path, java.util.LinkedHashMap options = [:], build = currentBuild)
{
    def computer = Jenkins.instance.getComputer(node)
    if (computer == null) {
        // check if master is under this label
        def is_master = jenkins.model.Jenkins.instance.getLabel(node).nodes.findAll{ it.class == hudson.model.Hudson }.size() > 0
        if (is_master) {
            // master node computer is found under parenthesis ... no idea why
            computer = Jenkins.instance.getComputer("(${node})")
        }
    }
    assert computer != null, "node '${node}' not found"
    writeLogsWithBranchInfo(new hudson.FilePath(computer.channel, path), options, build)
}


// get list of branches and parents
// (list of list one item per branch, parents first, null if no branch name)
// each item of the list can be used as filter
@NonCPS
java.util.ArrayList getBranches(java.util.LinkedHashMap options = [:], build = currentBuild)
{
    // return value
    def result = []

    // 1/ parse options
    def opt = _parseOptions(options)

    def tree = _getNodeTree(build)

    if (this.verbose) {
        print "tree=${tree}"
    }

    tree.values().each {
        def branches = _getBranches(tree, it.id, opt.showStages, opt.mergeNestedDuplicates)

        if (branches.size() == 0) {
            // no branch is represented as [null] when filtering
            // to distinguish from [] : all branches
            branches = [ null ]
        }

        def keep = _keepBranches(branches, opt.filter)

        // reverse order to match filtering order (parents first)
        branches = branches.reverse()

        if (keep && ! (branches in result) ) {
            result += [ branches ]
        }
    }
    return result
}

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

return this
