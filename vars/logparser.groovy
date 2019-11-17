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
    verbose = v
}

//***************
//* LOG PARSING *
//***************

// return list of maps describing the logs offsets and workflow ids
// [ { id: id, start: start, stop: stop }* ]
// id can be null (technical part of the logs)
// cf https://stackoverflow.com/a/57351397
@NonCPS
List<java.util.LinkedHashMap> getLogIndex() {

    // return value
    def logIndex = []

    // read log-index file
    // (no stream to avoid infinite loop while parsing it: it shall grow as long as logs are printed)
    def jobRoot = currentBuild.rawBuild.getRootDir()
    def logIndexFile = new File(jobRoot, 'log-index')
    assert logIndexFile.exists()

    // format of a line : <start offset> <id>
    // if no id it's a block without id (technical pipeline log)

    def previousStart = 0
    def previousId = null
    for (line in logIndexFile.text.split('\n')) {
        def logIndexItems = line.split(' ')
        assert logIndexItems.size() == 1 || logIndexItems.size() == 2, 'failed to parse log-index file'
        def start = Integer.parseInt(logIndexItems[0])
        def id = null
        if (logIndexItems.size() == 2) {
            id = Integer.parseInt(logIndexItems[1])
        }
        assert start > previousStart, 'failed to parse log-index file'

        logIndex += [ [ id: previousId, start: previousStart, stop: start ] ]
        previousStart = start
        previousId = id
    }

    if (verbose) {
        //print "logIndexFile=${logIndexFile.text}"
        print "logIndex=${logIndex}"
    }

    return logIndex
}

// return map describing the content of each branch:
// - branch name (if any otherwise null)
// - workflow ids of children
// - nested branches ids
// - parent branch id
// - branch name of all parents (parent, grandParent, etc ...)
// { id: { name: brancheName, children: [id ...], nested: [id, ...], parent: id, parentNames: [ parentName, grandParentName, ...] } }
// cf https://stackoverflow.com/a/57351397
@NonCPS
java.util.LinkedHashMap getWorkflowBranchMap() {

    // return value
    def branchMap = [:]

    def jobRoot = currentBuild.rawBuild.getRootDir()

    // parse workflow/*.xml to get workflow ids and parents

    def workflow = new File(jobRoot, 'workflow')
    assert workflow.exists()
    assert workflow.isDirectory()
    def fileList = workflow.listFiles()
    // sort by name, name must be <number>.xml
    fileList.each { assert it.name ==~ /^[0-9]*\.xml$/ }
    fileList = fileList.sort { a,b -> Integer.parseInt(a.name.replace('.xml','')) <=> Integer.parseInt(b.name.replace('.xml','')) }

    // temporary map of parent branches
    def parentBranchMap = [:]

    for (file in fileList) {
        def rootnode = new XmlSlurper().parse(file.path)
        def parents = rootnode.node.parentIds.string.collect{ Integer.parseInt("$it") }
        def id = Integer.parseInt("${rootnode.node.id}")
        def nodeClass = rootnode.node.@'class'

        //if (verbose) {
        //    print "file ${file.path}: class=${nodeClass} id=${id} parents=${parents}"
        //}

        // start of branch: record branch name
        if (nodeClass == 'cps.n.StepStartNode') {
            def descriptorId = rootnode.node.descriptorId
            def name = null
            if (descriptorId == 'org.jenkinsci.plugins.workflow.cps.steps.ParallelStep') {
                name = rootnode.actions.'org.jenkinsci.plugins.workflow.cps.steps.ParallelStepExecution_-ParallelLabelAction'.branchName.toString()
                // if node is missing default value is ''
                if (name == '') {
                    name = null
                }
            }

            branchMap."$id" = [:]
            branchMap."$id".children = []
            branchMap."$id".nested = []
            branchMap."$id".name = name

            //if (verbose) {
            //    print "file ${file.path}: branchMap.$id=${branchMap."$id"}"
            //}
        }

        if (nodeClass == 'org.jenkinsci.plugins.workflow.graph.FlowStartNode') {
            assert parents.size() == 0
            // no parent, start family tree
            branchMap."$id" = [:]
            branchMap."$id".children = []
            branchMap."$id".nested = []
            branchMap."$id".name = null
            branchMap."$id".parent = null
        }
        else {
            assert parents.size() > 0
            def branchParentId

            if (nodeClass == 'cps.n.StepEndNode') {
                // end of branch
                branchParentId = Integer.parseInt("${rootnode.node.startId}")
            }
            else {
                // not a end of branch so one parent only
                assert parents.size() == 1
                branchParentId = parents[0]
            }

            // if the parent is not a branch but the child of a branch
            // use that branch id instead: it is the true branch parent
            if (
                ! ( branchParentId in branchMap.keySet().collect{ Integer.parseInt("$it") } ) &&
                ( branchParentId in parentBranchMap.keySet().collect{ Integer.parseInt("$it") } )
            ) {
                branchParentId = parentBranchMap."$branchParentId"
            }

            parentBranchMap."$id" = branchParentId
            if ( branchParentId in branchMap.keySet().collect{ Integer.parseInt("$it") } ) {
                if ( id in branchMap.keySet().collect{ Integer.parseInt("$it") } ) {
                    branchMap."$branchParentId".nested += [ id ]
                    branchMap."$id".parent = branchParentId
                } else {
                    branchMap."$branchParentId".children += [ id ]
                }
            }

            //if (verbose) {
            //    print "file ${file.path}: branchParentId=${branchParentId}"
            //}
        }
    }

    // fill in list of parent branches (next parent first)
    branchMap.each { k, v ->
        v.parentNames = []
        def next = v.parent
        while (next) {
            if (branchMap."${next}".name) {
                v.parentNames += [ branchMap."${next}".name ]
            }
            next = branchMap."${next}".parent
        }
    }

    if (verbose) {
        print "parentBranchMap=${parentBranchMap}"
        print "branchMap=${branchMap}"
    }

    return branchMap
}


// return list of maps describing the logs offsets, workflow ids and branche name(s)
// [ { id: id, start: start, stop: stop, branches: [ branch1, branch2, ... ] }* ]
// id and branches can be null. branches contain all nested branch (starting with the nested one)
@NonCPS
List<java.util.LinkedHashMap> getLogIndexWithBranches() {

    // 1/ read log-index file with log offsets first
    // (read it before to parse id files to have only known ids)
    def logIndex = getLogIndex()

    // 2/ get branch information
    def branchMap = getWorkflowBranchMap()

    // and use branchMap to fill reverse map for all ids : for each id find which branch(es) it belong to
    def idBranchMap = [:]
    branchMap.each { k, v ->
        v.children.each {
            // each id should appear as child of one branch only
            assert idBranchMap."$it" == null
            if (v.name) {
                idBranchMap."$it" = [v.name] + v.parentNames
            } else {
                idBranchMap."$it" = v.parentNames
            }
        }
        if (v.name) {
            idBranchMap."$k" = [v.name] + v.parentNames
        } else {
            idBranchMap."$k" = v.parentNames
        }
    }

    if (verbose) {
        print "idBranchMap=${idBranchMap}"
    }

    // finally fill the logIndex with list of branches
    def logIndexWithBranches = logIndex.collect {
        if (it.id) {
            assert idBranchMap."${it.id}" != null
            return [ id: it.id, start: it.start, stop: it.stop, branches: idBranchMap."${it.id}" ]
        } else {
            return [ id: null, start: it.start, stop: it.stop, branches: [] ]
        }
    }

    if (verbose) {
        print "logIndexWithBranches=${logIndexWithBranches}"
    }

    return logIndexWithBranches
}

//***************************
//* LOG FILTERING & EDITING *
//***************************

// remove log VT100 markups ( ESC[8m.*ESC[0m ) which make logfile hard to read
// cf http://ascii-table.com/ansi-escape-sequences-vt-100.php
// cf https://www.codesd.com/item/how-to-delete-jenkins-console-log-annotations.html
// cf https://issues.jenkins-ci.org/browse/JENKINS-48344
@NonCPS
String removeVT100Markups(String buffer) {
    return buffer.replaceAll(/\x1B\[8m.*?\x1B\[0m/, '')
}

// return log file with BranchInformation
// - return logs only for one branch if filterBranchName not null (default null)
// - with parent information for nested branches if options.showParents is true (default)
//   example:
//      if true: "[branch2] [branch21] log from branch21 nested in branch2"
//      if false "[branch21] log from branch21 nested in branch2"
// - with a marker showing nested branches if options.markNestedFiltered is true (default) and if filterBranchName is not null
//   example:
//      "[ filtered 6787 bytes of logs for nested branches: branch2.branch21 branch2.branch22 ] (...)"
// - with vt100 markups removed if options.hideVT100 is true (default)
//
// cf https://stackoverflow.com/questions/38304403/jenkins-pipeline-how-to-get-logs-from-parallel-builds
// cf https://stackoverflow.com/a/57351397
// (workaround for https://issues.jenkins-ci.org/browse/JENKINS-54304)
@NonCPS
String getLogsWithBranchInfo(java.util.LinkedHashMap options = [:])
{
    // return value
    def output = ''

    // 1/ parse options
    options.keySet().each{ assert it in ['filter', 'showParents', 'markNestedFiltered', 'hideVT100'], "invalid option $it" }

    // name of the branch to filter. default value null
    def filterBranchName = options.filter

    // show parent branch(es) name(s) if nested branch
    def showParents = options.showParents == null ? true : options.showParents

    // highlight nested branches filtered when filterBranchName is not null
    // technically they are a sub-part of the branch we are filtering
    // but showing them might show logs from mutiple branches: better to filter them 1 by 1 (caller decision)
    // put a marker in log to indicate that logs for those branches were filtered
    // "[ filtered 6787 bytes of logs for nested branches: branch2.branch21 branch2.branch22 ] (...)"
    def markNestedFiltered = options.markNestedFiltered == null ? true : options.markNestedFiltered

    // hide VT100 markups
    def hideVT100 = options.hideVT100 == null ? true : options.hideVT100

    def WJpluginVerList = Jenkins.instance.pluginManager.plugins.findAll{ it.getShortName() == 'workflow-job' }.collect { it.getVersion() }
    assert WJpluginVerList.size() == 1, 'could not fing workflow-job plugin version'
    def WJpluginVer = Float.parseFloat(WJpluginVerList[0])

    if (WJpluginVer > 2.25) {
        // get the log index before to read the logfile to make sure all items are in the file
        def logIndex = getLogIndexWithBranches()

        // Read the log file as byte[].
        def jobRoot = currentBuild.rawBuild.getRootDir()
        def logFile = new File("${jobRoot}/log")
        assert logFile.exists()
        def logStream = currentBuild.rawBuild.getLogInputStream()

        def filtered = 0
        def filteredBranches = [:]
        def filterMsg = { b, m ->
            def msg = ''
            if (markNestedFiltered) {
                // TODO: number of filtered lines rather than number of bytes
                msg = "[ filtered ${b} bytes of logs"
                if (m.size() != 0) {
                    msg += " for nested branches: ${m.keySet().join(' ')}"
                }
                msg +=  " ] (...)\n"
            }
            return msg
        }

        logIndex.each {
            if (it.branches.size() > 0) {
                def branchInfo = ''
                if (showParents) {
                    // reverse list to show parent branch first
                    branchInfo = it.branches.reverse().collect{ "[$it] " }.sum()
                } else {
                    branchInfo = "[${it.branches[0]}] "
                }

                if (
                    (filterBranchName == null) ||
                    (it.branches[0] == filterBranchName)
                ) {
                    if (filtered > 0) {
                        output += filterMsg.call(filtered, filteredBranches)
                        filtered = 0
                        filteredBranches = [:]
                    }
                    byte[] logs = new byte[it.stop - it.start]
                    assert logStream.read(logs) == it.stop - it.start
                    output += new String(logs, 0, it.stop - it.start, "UTF-8").split('\n').collect{ "${branchInfo}${it}" }.join('\n') + '\n'

                // (filterBranchName in it.branches) would not return true (.equals mismatch ...) use toString
                } else if (filterBranchName.toString() in it.branches.collect{ it.toString() }) {
                    // nested branches is filtered: record it
                    assert logStream.skip(it.stop - it.start)
                    filtered += it.stop - it.start
                    filteredBranches."${it.branches.reverse().join('.')}" = true
                } else {
                    assert logStream.skip(it.stop - it.start)
                }
            } else if (filterBranchName == null) {
                // not in a parallel branch
                byte[] logs = new byte[it.stop - it.start]
                assert logStream.read(logs) == it.stop - it.start
                output += new String(logs, 0, it.stop - it.start, "UTF-8")
            } else {
                assert logStream.skip(it.stop - it.start)
            }
        }

        if (filtered > 0) {
            output += filterMsg.call(filtered, filteredBranches)
        }

        if (hideVT100) {
            output = removeVT100Markups(output)
        }
    } else {
        // pre log-index version
        // parse logs the old way with a regexp since branches are already set
        // options showParents not implemented
        assert options.showParents == null || options.showParents == false, 'options showParents not implemented before workflow-job plugin 2.25 or earlier'
        // options markNestedFiltered implemented differently: nested branches are marked through the "[Pipeline] [BranchName]" prefixes which show start of a new branch

        output = currentBuild.rawBuild.log
        if (hideVT100) {
            output = removeVT100Markups(output)
        }
        if (filterBranchName != null) {
            // get all lines starting with [BranchName]
            def expr = /(?m)^\[${filterBranchName}\] .*$/

            // markNestedFiltered implies showing lines starting with [Pipeline] [BranchName]
            // it actually shows more than just nested branches
            if (markNestedFiltered) {
                expr = /(?m)^\[Pipeline\] \[${filterBranchName}\] .*$|(?m)^\[${filterBranchName}\] .*$/
            }

            output = (output =~ expr).collect{ it }.findAll{ it }.join('\n')
        }
    }

    return output
}

//*************
//* ARCHIVING *
//*************

// archive buffer directly on the master (no need to instantiate a node like ArchiveArtifact)
@NonCPS
void archiveArtifactBuffer(String name, String buffer) {
    def jobRoot = currentBuild.rawBuild.getRootDir()
    def file = new File("${jobRoot}/archive/${name}")

    if (verbose) {
        print "archiving ${file.path}"
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

