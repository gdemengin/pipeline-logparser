// library with functions adding prefix to each log belonging to a branch (like workflow-plugin used to do prior to version 2.25)
// also it allows
// - to filter logs by branchName
// - to show name of parent branches (as prefix in the logs) for nested branches
// - to hide (or show) VT100 markups
// 
// - and to archive files in job artifacts without having to allocate a node (same as ArchiveArtifacts but without node() scope)

// it is meant to be used a a "Global Pipeline Library" (Manage jenkins > Configure System > Global Pipeline Library)
// to avoid having to approve dangerous methods through "Manage Jenkins > In-process Script Approval"
// but it's also possible to copy the functions in a Jenkinsfile and use them from there (and approve whatever needs to be)


// set to true to add verbose information
def verbose = false

// remove log VT100 markups which make logfile hard to read (ESC[8mblablaESC[0m)
// cf http://ascii-table.com/ansi-escape-sequences-vt-100.php
// cf https://www.codesd.com/item/how-to-delete-jenkins-console-log-annotations.html
// cf https://issues.jenkins-ci.org/browse/JENKINS-48344
// TODO parse markup to extract useful information (timestamp)
@NonCPS
def removeVT100Markups(buffer) {
    return buffer.replaceAll(/\x1B\[8m.*?\x1B\[0m/, '')
}

// archive buffer directly on the master (no need to instantiate a node like ArchiveArtifact)
@NonCPS
def ArchiveArtifactBuffer(buffer, name) {
    def jobRoot = currentBuild.rawBuild.getRootDir()
    def file = new File("${jobRoot}/archive/${name}")
    if (! file.parentFile.exists()){
        file.parentFile.mkdirs();
    }
    file.write(buffer)
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

// return list of maps describing the logs offsets, workflow ids and branche name(s)
// [ { id: id, start: start, stop: stop, branches: branches }* ] 
// id and branches can be null. branches contain all nested branch (starting with the nested one
// cf https://stackoverflow.com/questions/38304403/jenkins-pipeline-how-to-get-logs-from-parallel-builds
// cf https://stackoverflow.com/a/57351397
// (workaround for https://issues.jenkins-ci.org/browse/JENKINS-54304)
@NonCPS
def getLogIndexWithBranches() {

    // return value
    def logIndex = []

    // 1/ read log-index file with log offsets first 
    // (no stream to avoid infinite loop while parsing it: it shall grow as long as logs are printed)
    // (read it before to parse id files to have only known ids)

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

    // 2/ parse workflow/*.xml to get workflow ids and parents

    def workflow = new File(jobRoot, 'workflow')
    assert workflow.exists()
    assert workflow.isDirectory()
    def fileList = workflow.listFiles()
    // sort by name, name must be <number>.xml
    fileList.each { assert it.name ==~ /^[0-9]*\.xml$/ }
    fileList = fileList.sort { a,b -> Integer.parseInt(a.name.replace('.xml','')) <=> Integer.parseInt(b.name.replace('.xml','')) }

    // temporary map of children, parents and branches
    def childrenMap = [:].withDefault { [] }
    def parentMap = [:]
    def branchMap = [:]

    for (file in fileList) {
        def rootnode = new XmlSlurper().parse(file.path)
        def parents = rootnode.node.parentIds.string.collect{ Integer.parseInt("$it") }
        def id = Integer.parseInt("${rootnode.node.id}")
        def branch = rootnode.actions."org.jenkinsci.plugins.workflow.cps.steps.ParallelStepExecution_-ParallelLabelAction".branchName

        if (branch != '') {
            branchMap."$id" = [:]
            branchMap."$id".name = branch.toString()
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

    // and use branchMap to fill reverse map : for each id find which branch(es) it belong to
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
    logIndex = logIndex.collect {
        if (it.id) {
            return [ id: it.id, start: it.start, stop: it.stop, branches: idBranchMap."${it.id}" ]
        } else {
            return [ id: null, start: it.start, stop: it.stop, branches: null ]
        }
    }

    if (verbose) {
        print "logIndex=${logIndex}"
    }

    return logIndex
}


@NonCPS
def getLogsWithBranches(filterBranchName = null, options = [:] )
{
    // options

    // show parent branch(es) name(s) if nested branch
    def showParents = options.showParents == null ? true : options.showParents

    // highlight nested branches filtered when filterBranchName is not null
    // technically they are a sub-part of the branch we are filtering 
    // but showing them might show logs from mutiple branches: better to filter them 1 by 1
    // put a marker in log to indicate that logs for those branches were filtered
    // "[ filtered 6787 bytes of logs for nested branches: branch2.branch21 branch2.branch22 ] (...)"
    def markNestedFiltered = options.markNestedFiltered == null ? true : options.markNestedFiltered

    // hide VT100 markups 
    def hideVT100 = options.hideVT100 == null ? true : options.hideVT100

    def output = ''

    // get the log index before to read the logfile to make sure all items are in the file
    def logIndex = getLogIndexWithBranches()

    if (verbose) {
        print "logIndex =${logIndex}"
    }

    // Read the log file as byte[].
    def logFile = currentBuild.rawBuild.getLogFile()
    def logs = logFile.bytes

    def filtered = 0
    def filteredBranches = [:]
    def filterMsg = { b, m ->
        def msg = ''
        if (markNestedFiltered) {
            // TODO: number of lines
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

    return output
}


