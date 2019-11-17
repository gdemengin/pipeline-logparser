// sample pipeline to test https://github.com/gdemengin/pipeline-logs/ "Global Pipeline Library" logparser


// import logparser library
@Library('pipeline-logparser@1.0.1') _

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

def runSingleBranches() {
    // parallel with only one branch
    // it's a way to generate logs (wish there was a dedicated log command to do that)
    parallel main: {
        parallel init: {
            print 1
        }
        parallel build: {
            print 1
        }
    }
}

def runBranchesWithManyLines(nblines) {
    // run branches with manylines and making sure lines are mixed between branches
    // without technical pipeline pieces of logs between each switch of branch in the logs
    def script = '''\
        set +x
        i=0
        while [ $i -lt ''' + nblines.toString() + '''  ]
        do
            echo line $i
            i=$(( $i+1 ))
            if [ $i = 50 ]
            then
                # sleep once in the middle to let the other branch do some logs
                sleep 1
            fi
        done
        '''
    script = script.stripIndent()
    print script
    parallel one: {
        node() {
            sh script
        }
    }, two: {
        node() {
            sh script
        }
    }
}

def runStagesAndBranches() {
    stage('stage1') {
        echo 'in stage1'
    }
    stage('stage2') {
        parallel 's2b1': {
            echo 'in stage2.s2b1'
        }, 's2b2': {
            echo 'in stage2.s2b2'
        }
    }
}

// =================================
// = parse logs and archive them   =
// =================================

def parseLogs() {

    // sleep 1s and use echo to flush logs before to call logparser
    // might not be enough
    // TODO: find a better way to make sure the log is complete (may be print a marker in the log and wait for it to appear ??)
    sleep 1
    echo ''

    // archivelogs (manual check required)

    // archive full logs
    timestamps {
        print 'before parsing and archiving'
        logparser.archiveLogsWithBranchInfo('consoleText.txt')
        print 'after parsing and archiving'
    }
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
    def logsBranch1 = logparser.getLogsWithBranchInfo(filter:'branch1')
    def logsBranch2 = logparser.getLogsWithBranchInfo(filter:'branch2')
    def logsBranch3 = logparser.getLogsWithBranchInfo(filter:'branch3')
    def fullLog = logparser.getLogsWithBranchInfo()
    print "logsBranch2='''${logsBranch2}'''"
    assert logsBranch2.contains('in branch1') == false
    assert logsBranch2.contains('in branch2')
    assert logsBranch3.size() == 0

    assert fullLog.contains('in branch1')
    assert fullLog.contains('in branch2')
    assert fullLog.contains('not in any branch')
    assert fullLog.contains('[main] [init]')
    assert fullLog.contains('[main] [build]')
}

runBranches()
runSingleBranches()
runBranchesWithManyLines(100)
runStagesAndBranches()
parseLogs()

// uncomment to test with 10 million lines (multiple hours of test, may fail if not enough heap space)
/*

runBranchesWithManyLines(1000)
timestamps {
    print 'before parsing'
    logparser.archiveLogsWithBranchInfo('manylines1000.txt')
    print 'after parsing'
}

runBranchesWithManyLines(10*1000)
timestamps {
    print 'before parsing'
    logparser.archiveLogsWithBranchInfo('manylines10000.txt')
    print 'after parsing'
}

runBranchesWithManyLines(100*1000)
timestamps {
    print 'before parsing'
    logparser.archiveLogsWithBranchInfo('manylines100000.txt')
    print 'after parsing'
}

runBranchesWithManyLines(1000*1000)
timestamps {
    print 'before parsing'
    logparser.archiveLogsWithBranchInfo('manylines1000000.txt')
    print 'after parsing'
}

runBranchesWithManyLines(10*1000*1000)
timestamps {
    print 'before parsing'
    logparser.archiveLogsWithBranchInfo('manylines10000000.txt')
    print 'after parsing'
}

*/
