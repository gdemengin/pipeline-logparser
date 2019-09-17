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
def getLogIndex() {

    // return value
    def logIndex = []

    // read log-index file
    // (no stream to avoid infinite loop while parsing it: it shall grow as long as logs are printed)
    def jobRoot = currentBuild.rawBuild.getRootDir()
    def logIndexFile = new File(jobRoot, 'log-index')
    assert logIndexFile.exists()

    // format of a line can be one of the following:
    // offset nodeId: start of new node range and end of the previous (if present : range without Id).
    // offset: end of current node range.

    // so logIndexList is a list with triplets : [ start, id, stop ]*
    // last triplet might be incomplete
    def logIndexList = logIndexFile.text.replaceAll('[\n\r]', ' ').split(' ').collect{ it }

    // parse complete triplets
    def index = 0
    for (def i = 0; i + 2 < logIndexList.size(); i += 3) {
        def start = Integer.parseInt(logIndexList[i])
        def id = Integer.parseInt(logIndexList[i+1])
        def stop = Integer.parseInt(logIndexList[i+2])

        if (index != start) {
            // no workflow id (probably pipeline technical log)
            logIndex += [ [ id: null, start: index, stop: start ] ]
        }

        logIndex += [ [ id: id, start: start, stop: stop ] ]

        index = stop
    }

    if (verbose) {
        print "logIndex=${logIndex}"
    }

    return logIndex
}

// recursive
// build list of children ids and list of nested branch ids for a particular workflow node
@NonCPS
def familyTree(nodeId, childrenMap, branchList) {
    def family = [:]
    // immediate family
    family.children = childrenMap."$nodeId".minus(branchList)
    family.nested = childrenMap."$nodeId".intersect(branchList)

    // 2nd generation and below
    def generation2 = family.children.collect{ familyTree(it, childrenMap, branchList) }

    generation2.each{
        family.children += it.children
        family.nested += it.nested
    }
    return family
}

// return map describing the content of each branch (workflow ids + nested branches)
// { id: { children: [id ...], nested: [id, ...], parent: id } }
// cf https://stackoverflow.com/a/57351397
@NonCPS
def getWorkflowParallelBranchMap() {

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

    // temporary map of children, parents
    def childrenMap = [:].withDefault { [] }
    def parentMap = [:]

    for (file in fileList) {
        def rootnode = new XmlSlurper().parse(file.path)
        def parents = rootnode.node.parentIds.string.collect{ Integer.parseInt("$it") }
        def id = Integer.parseInt("${rootnode.node.id}")
        def branch = rootnode.actions."org.jenkinsci.plugins.workflow.cps.steps.ParallelStepExecution_-ParallelLabelAction".branchName.toString()

        if (branch != '') {
            branchMap."$id" = [:]
            branchMap."$id".name = branch
        }

        if (parents.size() == 1) {
            parents.each {
                childrenMap."$it" += [id]
                parentMap."$id" = it
            }
        } else if (parents.size() > 1) {
            // multiple parents, this is a join of multiple branches
            // so the real parent is the parent branch of those multiple branches
            // find common parent in ParentMap
            // (needed for nested branches to know which branch this id really is)
            def fullParentLists = parents.collect {
                def fullParentList = []
                def next = it
                while(next) {
                    fullParentList += [ next ]
                    next = parentMap."$next"
                }
                return fullParentList
            }

            def commonParents = fullParentLists[0].intersect(fullParentLists[1])
            for (def i = 2; i < fullParentLists.size() ; i++) {
                commonParents = commonParents.intersect(fullParentLists[i])
            }

            // the first in the list is the parent
            // if none was found something is wrong
            assert commonParents.size() > 0
            childrenMap."${commonParents[0]}" += [id]
            parentMap."$id" = commonParents[0]
        }
    }

    if (verbose) {
        print "childrenMap=${childrenMap}"
        print "parentMap =${parentMap}"
    }

    // now fill branch map with children and parents
    branchMap.keySet().each {
        def family = familyTree(it, childrenMap, branchMap.keySet().collect{ Integer.parseInt(it) } )
        branchMap."$it".children = family.children
        branchMap."$it".nested = family.nested
        family.nested.each { n ->
            branchMap."$n".parent = it
        }
    }

    if (verbose) {
        print "branchMap=${branchMap}"
    }

    return branchMap
}


// return list of maps describing the logs offsets, workflow ids and branche name(s)
// [ { id: id, start: start, stop: stop, branches: branches }* ]
// id and branches can be null. branches contain all nested branch (starting with the nested one)
@NonCPS
def getLogIndexWithBranches() {

    // 1/ read log-index file with log offsets first
    // (read it before to parse id files to have only known ids)
    def logIndex = getLogIndex()

    // 2/ get branch information
    def branchMap = getWorkflowParallelBranchMap()

    // and use branchMap to fill reverse map for all ids : for each id find which branch(es) it belong to
    def idBranchMap = [:]
    branchMap.each { k, v ->
        v.children.each {
            // each id should appear in one branch only
            assert idBranchMap."$it" == null
            idBranchMap."$it" = [v.name]
            def next = v.parent
            while (next) {
                idBranchMap."$it" += [ branchMap."${next}".name ]
                next = branchMap."${next}".parent
            }
        }
    }

    if (verbose) {
        print "idBranchMap=${idBranchMap}"
    }

    // finally fill the logIndex with list of branches
    def logIndexWithBranches = logIndex.collect {
        if (it.id) {
            return [ id: it.id, start: it.start, stop: it.stop, branches: idBranchMap."${it.id}" ]
        } else {
            return [ id: null, start: it.start, stop: it.stop, branches: null ]
        }
    }

    if (verbose) {
        print "logIndexWithBranches=${logIndex}"
    }

    return logIndexWithBranches
}

//***************************
//* LOG FILTERING & EDITING *
//***************************

// remove log VT100 markups which make logfile hard to read (ESC[8mblablaESC[0m)
// cf http://ascii-table.com/ansi-escape-sequences-vt-100.php
// cf https://www.codesd.com/item/how-to-delete-jenkins-console-log-annotations.html
// cf https://issues.jenkins-ci.org/browse/JENKINS-48344
// TODO parse markup to extract useful information (timestamp)
@NonCPS
def removeVT100Markups(buffer) {
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
def getLogsWithBranchInfo(options = [:])
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
        def logFile = currentBuild.rawBuild.getLogFile()
        def logs = logFile.bytes

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
            if (it.branches != null) {
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
                    output += new String(logs, it.start, it.stop - it.start, "UTF-8").split('\n').collect{ "${branchInfo}${it}" }.join('\n') + '\n'

                // (filterBranchName in it.branches) would not return true (.equals mismatch ...) use toString
                } else if (filterBranchName.toString() in it.branches.collect{ it.toString() }) {
                    // nested branches is filtered: record it
                    filtered += it.stop - it.start
                    filteredBranches."${it.branches.reverse().join('.')}" = true
                }
            } else if (filterBranchName == null) {
                // not in a parallel branch
                output += new String(logs, it.start, it.stop - it.start, "UTF-8")
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
def archiveArtifactBuffer(buffer, name) {
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
def archiveLogsWithBranchInfo(name, options = [:])
{
    archiveArtifactBuffer(getLogsWithBranchInfo(options), name)
}


return this

