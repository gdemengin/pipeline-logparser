// sample pipeline to test https://github.com/gdemengin/pipeline-logs/ "Global Pipeline Library" logparser

// ============================
// = create parallel branches =
// = and nested branches      =
// ============================
def branches = [:]
def i, j, k, l
def loop=5

// simple branch with logs
branches.branch1 = {
    for (i=0; i < loop; i++) {
        sleep 1
        echo "i=$i in branch1"
    }
    echo 'in branch1'
}

// branch with nested branches
// one of the nested branches has the same name as branch1
branches.branch2 = {
    for (j=0; j < loop; j++) {
        sleep 1
        echo "j=$j in branch2"
    }

    def nestedBranches = [:]
    nestedBranches.branch21 = {
        for (k=0; k < loop; k++) {
            sleep 1
            echo "k=$k in branch2.branch21"
        }
    }
    echo 'in branch2 before to run nested branches'
    nestedBranches.branch2 = {
        for (l=0; l < loop; l++) {
            sleep 1
            echo "l=$l in branch2.branch22"
        }
    }
    // see how we manage to have 2 branches with the same name
    nestedBranches.branch1 = {
        echo 'in branch2.branch1'
    }
    parallel nestedBranches
}

// branch with no logs
branches.branch3 = {
    sleep 20
}

// run branches
parallel branches
echo 'this log is not in any branch'

// sleep 1 because sometimes the last log is missing
// is this always good enough ?
// TODO: find a better way to make sure the log is complete (may be put a marker in the log and wait for it to appear ??)
sleep 1


logparser = library(identifier:"pipeline-logs@master", changelog: false)

// get logs with [branch] prefix
def logsWithBranchInfo = logparser.getLogsWithBranchInfo()
logparser.ArchiveArtifactBuffer(logsWithBranchInfo, 'consoleTextWithBranch.txt' )

// same with vt100 info
def logsWithBranchAndVT100Info = logparser.getLogsWithBranchInfo( null, [hideVT100:false])
logparser.ArchiveArtifactBuffer(logsWithBranchAndVT100Info, 'consoleTextWithBranchAndVT100.txt')

// get log filtered by branch
def logsBranch1 = logparser.getLogsWithBranchInfo('branch1')
def logsBranch2 = logparser.getLogsWithBranchInfo('branch2')
def logsBranch3 = logparser.getLogsWithBranchInfo('branch3')

// branch name "branch1" is used twice (once as main branch, once as nested branch of branch2)
// it's possible to filter branch1 without showing parents of nested branches
def logsBranch1NoParents = logparser.getLogsWithBranchInfo('branch1', [showParents:false])

// branch "branch2" has nested branches: the nested logs are filtered out with a marker
// it's possible to hide the marker
def logsBranch2NoNestMarker = logparser.getLogsWithBranchInfo('branch2', [markNestedFiltered:false])

// =============================
// = archive logs in artifacts =
// =============================
logparser.ArchiveArtifactBuffer(logsWithBranchInfo, 'consoleTextWithBranchInfo.txt' )
logparser.ArchiveArtifactBuffer(logsWithBranchAndVT100Info, 'consoleTextWithBranchAndVT100Info.txt')
logparser.ArchiveArtifactBuffer(logsBranch1, 'consoleTextBranch1.txt')
logparser.ArchiveArtifactBuffer(logsBranch2, 'consoleTextBranch2.txt')
logparser.ArchiveArtifactBuffer(logsBranch3, 'consoleTextBranch3.txt')
logparser.ArchiveArtifactBuffer(logsBranch1NoParents, 'consoleTextBranch1NoParents.txt')
logparser.ArchiveArtifactBuffer(logsBranch2NoNestMarker, 'consoleTextBranch2NoNestMarker.txt')
