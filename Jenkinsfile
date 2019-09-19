// sample pipeline to test https://github.com/gdemengin/pipeline-logs/ "Global Pipeline Library" logparser


// import logparser library
@Library('pipeline-logparser@master') _

// uncomment if needed
// logparser.setVerbose(true)


// =========================
// = run parallel branches =
// = and nested branches   =
// =========================

// alternate sleeps and echo to have mixed logs
def testBranch(name, loop) {
    for (def i=0; i < loop; i++) {
        sleep 1
        echo "i=$i in $name"
    }
    echo "in $name"
}

def runBranches() {
    def branches = [:]
    def loop=2

    // simple branch with logs
    branches.branch1 = {
        testBranch('branch1', loop)
    }

    // branch with nested branches
    // one of the nested branches has the same name as branch1
    branches.branch2 = {
        testBranch('branch2', loop)

        def nestedBranches = [:]
        nestedBranches.branch21 = {
            testBranch('branch2.branch21', loop)
        }
        echo 'in branch2 before to run nested branches'
        nestedBranches.branch22 = {
            testBranch('branch2.branch22', loop)
        }

        // see how we manage to have 2 branches with the same name
        nestedBranches.branch1 = {
            testBranch('branch2.branch1', loop)
        }
        parallel nestedBranches
    }

    // branch with no logs
    branches.branch3 = {
        def k = 0
        k += 1
    }

    // run branches
    parallel branches
    echo 'this log is not in any branch'
}

runBranches()


// =================================
// = parse logs and archive them   =
// =================================

def parseLogs() {

    // sleep 1s before to parse logs because sometimes the last lines of log are missing
    // but is 1s always good enough ???
    // TODO: find a better way to make sure the log is complete (may be print a marker in the log and wait for it to appear ??)
    sleep 1

    // archive full logs
    logparser.archiveLogsWithBranchInfo('consoleText.txt')
    // same with vt100 markups
    logparser.archiveLogsWithBranchInfo('consoleText.vt100.txt', [hideVT100:false])
    // logs without parent branch info for nested branches (this info was not visible before 2.25)
    logparser.archiveLogsWithBranchInfo('consoleText.noparent.txt', [showParents:false])

    // archive logs from each branch
    logparser.archiveLogsWithBranchInfo('consoleText.branch1.txt', [filter:'branch1'])
    logparser.archiveLogsWithBranchInfo('consoleText.branch2.txt', [filter:'branch2'])
    logparser.archiveLogsWithBranchInfo('consoleText.branch3.txt', [filter:'branch3'])

    // branch2 logs without mention of filtered nested branches
    logparser.archiveLogsWithBranchInfo('consoleText.branch2.nonestedmark.txt', [filter:'branch2', markNestedFiltered:false])


    // access logs programmatically
    def logsBranch1 = logparser.getLogsWithBranchInfo([filter:'branch1'])
    assert logsBranch1.contains('in branch1') == true

}

parseLogs()

