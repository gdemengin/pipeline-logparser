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
            flowGraphMap[it.enclosingId].children.add(it.id)

            def infos = _getNodeInfos(it)
            flowGraphMap[it.id] += infos
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
        }
        else if (node.descriptor instanceof org.jenkinsci.plugins.workflow.support.steps.StageStep$DescriptorImpl) {
            def labelAction = node.actions.findAll { it.class == org.jenkinsci.plugins.workflow.actions.LabelAction }
            assert labelAction.size() == 1 || labelAction.size() == 0
            if (labelAction.size() == 1) {
                infos += [ name: labelAction[0].displayName, isStage: true ]
            }
        }
        else if (node.descriptor instanceof org.jenkinsci.plugins.workflow.support.steps.ExecutorStep$DescriptorImpl && node.displayName=='Allocate node : Start') {
            def argAction = node.actions.findAll { it.class == org.jenkinsci.plugins.workflow.cps.actions.ArgumentsActionImpl }
            assert argAction.size() == 1 || argAction.size() == 0
            // record the label if any
            if (argAction.size() == 1 && argAction[0].unmodifiedArguments) {
                infos += [ label: argAction[0].argumentsInternal.label ]
            }

            def wsAction = node.actions.findAll { it.class == org.jenkinsci.plugins.workflow.support.actions.WorkspaceActionImpl }
            // hostname may be missing if host not yet allocated
            assert wsAction.size() == 1 || wsAction.size() == 0
            // record host if any
            if (wsAction.size() == 1) {
                infos += [ host: wsAction[0].node ]
            }
        }
    }
    return infos
}

@NonCPS
java.util.LinkedHashMap _getNodeTree(build, _flowGraphMap = null, nodeId = null) {
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

    def node = flowGraphMap.map[nodeId ?: flowGraphMap.start].node

    // add current node to cache if not already there
    // or update it, if it was still active in cache (and possibly incomplete)
    if (this.cachedTree[key].containsKey(node.id) == false || this.cachedTree[key][node.id].active) {
        def children = flowGraphMap.map[node.id].children.sort{ Integer.parseInt("${it}") }
        def parent = node.enclosingId
        def parents = []
        if (parent != null) {
            parents = [parent] + this.cachedTree[key][parent].parents
        }

        // nodes are added in ordre (parents first)
        // copy all lists to avoid storing reference
        this.cachedTree[key][node.id] = [ \
            id: node.id,
            stage: flowGraphMap.map[node.id].isStage == true,
            branch: flowGraphMap.map[node.id].isBranch == true,
            parents: parents.collect { it },
            parent: parent,
            children: children.collect { it },
            branches: parents.findAll{ flowGraphMap.map[it].isBranch },
            stages: parents.findAll{ flowGraphMap.map[it].isStage },
            active: flowGraphMap.map[node.id].active == true,
            haslog: _getLogAction(node) != null,
            displayFunctionName: node.displayFunctionName,
            url: node.url
        ]
        if (flowGraphMap.map[node.id].name) {
           this.cachedTree[key][node.id] += [ name: flowGraphMap.map[node.id].name ]
        }
        if (flowGraphMap.map[node.id].host) {
            this.cachedTree[key][node.id] += [ host: flowGraphMap.map[node.id].host ]
        }
        if (flowGraphMap.map[node.id].label) {
            this.cachedTree[key][node.id] += [ label: flowGraphMap.map[node.id].label ]
        }

        // then add children
        children.each{
            _getNodeTree(build, flowGraphMap, it)
        }
    }
    // else : node was already put in tree while inactive, nothing to update

    return this.cachedTree[key]
}


@NonCPS
java.util.ArrayList _mergeNestedDuplicates(java.util.ArrayList branches) {
    // remove consecutive duplicates
    // this would happen if 2 nested parallel steps or stages have the same name
    // which is exactly what happens in declarative matrix (parallel step xxx contain stage xxx)
    def i = 0
    return branches.findAll {
        i++ == 0 || branches[i - 2] != it
    }
}


@NonCPS
java.util.ArrayList _getBranches(java.util.LinkedHashMap tree, id, java.util.LinkedHashMap options) {
    def branches = tree[id].branches.collect { it }
    if (options.showStages) {
        branches += tree[id].stages
    }

    // add current node as leading branch ... if it is a branch to show
    if (
        tree[id].branch ||
        (options.showStages && tree[id].stage)
    ) {
        branches += [id]
    }
    branches = branches.sort { Integer.parseInt("${it}") }.reverse()

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

    tree.values().findAll{ it.parent == null || it.stage || it.branch }.each {
        def url = "${rootUrl}pipeline/${it.id}"
        def log = "${restUrl}nodes/${it.id}/log/?start=0"
        if (it.parent == null) {
            url = "${rootUrl}pipeline"
            log = "${restUrl}log/?start=0"
        }
        // if more than one stage blue ocean urls are invalid
        def parents = (it.branches + it.stages).sort { Integer.parseInt("${it}") }.reverse()
        def parent = parents.size() > 0 ? parents[0] : null
        def item = [
            id: it.id,
            name: it.name,
            stage: it.stage,
            parents: parents,
            parent: parent,
            url: url,
            log: log
        ]
        ret += [ item ]
    }

    if (this.verbose) {
        print "BlueOceanUrls=${ret}"
    }
    return ret
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
        def item = [
            id: it.id,
            stage: it.stage,
            parents: it.parents,
            parent: it.parent,
            children: it.children,
            url: url
        ]
        if (it.haslog) {
            item += [ log: "${url}log", wfapiLog: "${url}wfapi/log" ]
        }
        if (it.name) {
            item += [ name: it.name ]
        }
        if (it.label) {
            item += [ label: it.label ]
        }
        if (it.host) {
            item += [ host: it.host ]
        }
        ret += [ item ]
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
        filter: null,
        showParents: true,
        showStages: true,
        markNestedFiltered: true,
        mergeNestedDuplicates: true,
        hidePipeline: true,
        hideVT100: true
    ]
    // merge 2 maps with priority to options values
    def new_options = defaultOptions.plus(options)
    new_options.keySet().each{
        assert it in defaultOptions.keySet(), "invalid option ${it} not in ${defaultOptions.keySet()}"
    }

    return new_options
}

@NonCPS
String _getPrefix(java.util.LinkedHashMap tree, java.util.ArrayList branches, java.util.LinkedHashMap options) {
    def names = branches.collect { tree[it].name }.findAll { it != null }

    def prefix = ''
    if (names.size() > 0) {
        if (options.showParents) {
            // remove duplicates (if needed)
            if (options.mergeNestedDuplicates) {
                names = _mergeNestedDuplicates(names)
            }
            prefix = names.reverse().collect{ "[$it] " }.sum()
        }
        else {
            prefix = "[${names[0]}] "
        }
    }
    return prefix
}

@NonCPS
Boolean _keepBranches(java.util.LinkedHashMap tree, java.util.ArrayList branches, java.util.LinkedHashMap options) {
    if (options.filter == null) {
        // no filter: keep everything
        return true
    }
    else if (options.filter in Collection) {
        // filter is a list of regexp or a list of list of regexp
        return false ||
            // no filter: keep everything
            options.filter.size() == 0 ||
            // no branch (i.e. main branch),
            // keep if filter is null (special filter for main branch)
            (branches.size() == 0 && null in options.filter) ||
            // else if one of the filters matches, keep the node
            (branches.size() > 0 && options.filter.count {
                def index = 0
                return false ||
                    // main branch: keep if filter is null
                    (it == null && tree[branches[0]].name == null) ||
                    // if filter is a string: use as regexp
                    (it in CharSequence && tree[branches[0]].name ==~ /^${it.toString()}$/) ||
                    // if filter is a list of regexp: all must match the N last branches
                    (it in Collection && branches.size() >= it.size() && it.reverse().every {
                        tree[branches[index++]].name ==~ /^${it.toString()}$/
                    })
            } > 0)
    }
    else {
        error "unexpected type for option filter ${options.filter}"
    }
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
    }
    else {
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
        }
        else {
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
        def branches = _getBranches(tree, it.id, opt)

        keep[it.id] = _keepBranches(tree, branches, opt)
        def prefix = _getPrefix(tree, branches, opt)

        if (keep[it.id]) {
            // no filtering or kept branch: keep logs

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
                }
                else {
                    logaction.logText.writeRawLogTo(0, b)
                }
                _appendToOutput(output, b.toString(), prefix)
            }
        }
        else if (opt.markNestedFiltered && it.id in branches && branches.size > 0 && keep[branches[1]]) {
            // branch is not kept (not in filter) but its parent branch is kept: record it as filtered
            _appendToOutput(output, "<nested branch ${prefix.replaceFirst(/ $/, '')}>\n")
        }
        // else parent branch is already discarded, nothing to show for this node
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
// each item of the list can be used to build filter
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
        def branches = _getBranches(tree, it.id, opt)
        def keep = _keepBranches(tree, branches, opt)

        branches = branches.collect { tree[it].name }.findAll { it != null }
        if (opt.mergeNestedDuplicates) {
            branches = _mergeNestedDuplicates(branches)
        }
        if (branches.size() == 0) {
            // no branch is represented as [null] when filtering
            // to distinguish from [] : all branches
            branches = [ null ]
        }

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
