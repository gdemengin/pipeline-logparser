// test pipeline for https://github.com/gdemengin/pipeline-logparser/


properties([
    parameters([
        booleanParam(defaultValue: false, description: 'set to to true to run extra long tests (multiple hours + may fail if not enough heap)', name: 'FULL_LOGPARSER_TEST'),
        booleanParam(defaultValue: false, description: 'FULL_LOGPARSER_TEST + even more aggressive: with log editing', name: 'FULL_LOGPARSER_TEST_WITH_LOG_EDIT'),
        booleanParam(defaultValue: false, description: 'run multithread timing test (may last a few hours + manual check of elapsed time)', name: 'MANYTHREAD_TIMING_TEST')
    ])
])

// ===============
// = constants   =
// ===============

// label on the instance with linux hosts
// leave null if all hosts fit the description
LABEL_LINUX='linux'

// set to to true to run extra long tests
// (multiple hours + may fail if not enough heap)
RUN_FULL_LOGPARSER_TEST = params.FULL_LOGPARSER_TEST == true
// even more aggressive: with log editing
RUN_FULL_LOGPARSER_TEST_WITH_LOG_EDIT = params.FULL_LOGPARSER_TEST_WITH_LOG_EDIT == true
// test with many threads to check the time spent
RUN_MANYTHREAD_TIMING_TEST = params.MANYTHREAD_TIMING_TEST == true

// ============================
// = import logparser library =
// ============================
// @Library('pipeline-logparser@2.0.1') _
node(LABEL_LINUX) {
    checkout scm
    def rev=sh(script: 'git rev-parse --verify HEAD', returnStdout: true).trim()
    library(identifier: "pipeline-logparser@${rev}", changelog: false)
}


// =============
// = globals   =
// =============

// uncomment if needed
// logparser.setVerbose(true)

// =====================
// = logparser tests   =
// =====================

// =========================
// = run parallel branches =
// = and nested branches   =
// =========================

// alternate sleeps and echo to have mixed logs
def testBranch(name, loop, expectedLogMap) {
    def line
    expectedLogMap."$name" = ''
    for (def i=0; i < loop; i++) {
        sleep 1
        expectedLogMap."$name" += 'Sleeping for 1 sec\n'
        line="i=$i in $name"
        echo line
        expectedLogMap."$name" += line + '\n'
    }
    line="in $name"
    echo line
    expectedLogMap."$name" += line + '\n'
}

def runBranches(expectedLogMap) {
    def line
    def branches = [:]
    def loop=2

    // simple branch with logs
    branches.branch0 = {
        testBranch('branch0', loop, expectedLogMap)
    }

    // simple branch with logs both here and nested in branch2
    branches.branch1 = {
        testBranch('branch1', loop, expectedLogMap)
    }

    // branch with nested branches
    // one of the nested branches has the same name as branch1
    branches.branch2 = {
        testBranch('branch2', loop, expectedLogMap)

        def nestedBranches = [:]
        nestedBranches.branch21 = {
            testBranch('branch2.branch21', loop, expectedLogMap)
        }
        line='in branch2 before to run nested branches'
        echo line
        expectedLogMap.'branch2' += line + '\n'
        nestedBranches.branch22 = {
            testBranch('branch2.branch22', loop, expectedLogMap)
        }

        // see how we manage to have 2 branches with the same name
        nestedBranches.branch1 = {
            testBranch('branch2.branch1', loop, expectedLogMap)
        }
        parallel nestedBranches
        expectedLogMap.'branch2' += '<nested branch [branch2] [branch21]>\n'
        expectedLogMap.'branch2' += '<nested branch [branch2] [branch22]>\n'
        expectedLogMap.'branch2' += '<nested branch [branch2] [branch1]>\n'
    }

    // branch with no logs
    expectedLogMap.'branch3' = ''
    branches.branch3 = {
        def k = 0
        k += 1
    }

    // run branches
    parallel branches
    expectedLogMap.'null' += '<nested branch [branch0]>\n'
    expectedLogMap.'null' += '<nested branch [branch1]>\n'
    expectedLogMap.'null' += '<nested branch [branch2]>\n'
    expectedLogMap.'null' += '<nested branch [branch2] [branch21]>\n'
    expectedLogMap.'null' += '<nested branch [branch2] [branch22]>\n'
    expectedLogMap.'null' += '<nested branch [branch2] [branch1]>\n'
    expectedLogMap.'null' += '<nested branch [branch3]>\n'

    line='this log is not in any branch'
    echo line
    expectedLogMap."null" += line + '\n'
}

def runSingleBranches(expectedLogMap) {
    def line='test single parallel branch'
    print line
    expectedLogMap.'null' += line + '\n'

    // parallel with only one branch
    // it's a way to generate block of logs (wish there was a dedicated log command to do that)
    expectedLogMap.'init' = ''
    parallel init: {
        line=1
        print line
        expectedLogMap."init" += line + '\n'
    }

    expectedLogMap.'empty' = ''
    parallel empty: {
    }

    expectedLogMap.'endl' = ''
    parallel endl: {
        print ''
        expectedLogMap."endl" += '\n'
    }

    // same with nested tasks
    expectedLogMap.'main' = ''
    parallel main: {
        expectedLogMap.'main.build' = ''
        parallel build: {
            line=2
            print line
            expectedLogMap."main.build" += line + '\n'
        }
        expectedLogMap.'main.test' = ''
        parallel test: {
            line='\n\ntest empty lines\n\nand more empty lines\n\n'
            print line
            expectedLogMap."main.test" += line + '\n'
        }
        expectedLogMap.'main' += '<nested branch [main] [build]>\n'
        expectedLogMap.'main' += '<nested branch [main] [test]>\n'
    }
    expectedLogMap.'null' += '<nested branch [init]>\n'
    expectedLogMap.'null' += '<nested branch [empty]>\n'
    expectedLogMap.'null' += '<nested branch [endl]>\n'
    expectedLogMap.'null' += '<nested branch [main]>\n'
    expectedLogMap.'null' += '<nested branch [main] [build]>\n'
    expectedLogMap.'null' += '<nested branch [main] [test]>\n'
}

def runBranchesWithManyLines(nblines, expectedLogMap) {
    // run branches with manylines and making sure lines are mixed between branches
    // without technical pipeline pieces of logs between each switch of branch in the logs
    def script = '''\
        set +x
        echo "+ set +x" > output.txt
        i=0
        while [ $i -lt ''' + nblines.toString() + ''' ]
        do
            echo line $i
            ''' + (expectedLogMap != null ? 'echo line $i >> output.txt' : '') + '''
            i=$(( $i+1 ))
            if [ $i = 50 ]
            then
                # sleep once in the middle to make sure the other branch adds some logs in the middle
                sleep 1
            fi
        done
        '''
    script = script.stripIndent()

    print script
    if (expectedLogMap != null) {
        expectedLogMap.'null' += script + '\n'

        expectedLogMap.'one' = ''
        expectedLogMap.'two' = ''
    }

    def start = 0
    parallel one: {
        print 'STRIP_NODE_LOG_START'
        node(LABEL_LINUX) {
            timeout(5) { // wait for other branch ... but no more than 5 minutes to avoid deadlock
                print 'STRIP_NODE_LOG_STOP'
                start += 1
                while (start < 2) {}
            }
            sh script
            if (expectedLogMap != null) {
                expectedLogMap.'one' += readFile('output.txt')
            }
        }
    }, two: {
        print 'STRIP_NODE_LOG_START'
        node(LABEL_LINUX) {
            timeout(5) { // wait for other branch ... but no more than 5 minutes to avoid deadlock
                print 'STRIP_NODE_LOG_STOP'
                start += 1
                while (start < 2) {}
            }
            sh script
            if (expectedLogMap != null) {
                expectedLogMap.'two' += readFile('output.txt')
            }
        }
    }
    if (expectedLogMap != null) {
        expectedLogMap.'null' += '<nested branch [one]>\n'
        expectedLogMap.'null' += '<nested branch [two]>\n'
    }
}

def runStagesAndBranches(expectedLogMapWithStages, expectedLogMap) {
    def line = 'testing stages'
    print line
    expectedLogMap.'null' += line + '\n'
    expectedLogMapWithStages.'null' += line + '\n'

    expectedLogMapWithStages.'stage1' = ''
    stage('stage1') {
        line='in stage1'
        echo line
        expectedLogMap.'null' += line + '\n'
        expectedLogMapWithStages.'stage1' += line + '\n'
    }

    expectedLogMapWithStages.'stage2' = ''
    stage('stage2') {
        expectedLogMap.'s2b1' = ''
        expectedLogMap.'s2b2' = ''
        expectedLogMapWithStages.'stage2.s2b1' = ''
        expectedLogMapWithStages.'stage2.s2b2' = ''
        expectedLogMapWithStages.'stage2.stage3' = ''

        stage('stage3') {
            line='in stage2.stage3'
            echo line
            expectedLogMap.'null' += line + '\n'
            expectedLogMapWithStages.'stage2.stage3' += line + '\n'
        }

        parallel 's2b1': {
            line='in stage2.s2b1'
            echo line
            expectedLogMap.'s2b1' += line + '\n'
            expectedLogMapWithStages.'stage2.s2b1' += line + '\n'
        }, 's2b2': {
            line='in stage2.s2b2'
            echo line
            expectedLogMap.'s2b2' += line + '\n'
            expectedLogMapWithStages.'stage2.s2b2' += line + '\n'
        }

        expectedLogMap.'null' += '<nested branch [s2b1]>\n'
        expectedLogMap.'null' += '<nested branch [s2b2]>\n'
        expectedLogMapWithStages.'stage2' += '<nested branch [stage2] [stage3]>\n'
        expectedLogMapWithStages.'stage2' += '<nested branch [stage2] [s2b1]>\n'
        expectedLogMapWithStages.'stage2' += '<nested branch [stage2] [s2b2]>\n'
    }
    expectedLogMapWithStages.'null' += '<nested branch [stage1]>\n'
    expectedLogMapWithStages.'null' += '<nested branch [stage2]>\n'
    expectedLogMapWithStages.'null' += '<nested branch [stage2] [stage3]>\n'
    expectedLogMapWithStages.'null' += '<nested branch [stage2] [s2b1]>\n'
    expectedLogMapWithStages.'null' += '<nested branch [stage2] [s2b2]>\n'
}

// =======================================
// = parse logs and archive/check them   =
// =======================================

def archiveStringArtifact(name, buffer) {
     logparser.archiveArtifactBuffer(name, buffer)
}

def checkLogs(log1, editedLog1, name1, log2, editedLog2, name2) {
    def tocmp1 = editedLog1 == null ? log1 : editedLog1
    def tocmp2 = editedLog2 == null ? log2 : editedLog2

    if (tocmp1 != tocmp2) {
        // TODO: print side by side differences
        print "${name1} = '''\\\n${log1}'''"
        archiveStringArtifact("check/${name1}.txt", log1)
        if (log1 != tocmp1) {
            print "${name1} (edited) ='''\\\n${tocmp1}'''"
            archiveStringArtifact("check/${name1}.edited.txt", tocmp1)
        }
        print "${name2} = '''\\\n${log2}'''"
        archiveStringArtifact("check/${name2}.txt", log2)
        if (log2 != tocmp2) {
            print "${name2} (edited) ='''\\\n${tocmp2}'''"
            archiveStringArtifact("check/${name2}.edited.txt", tocmp2)
        }
        error "${name1} and ${name2} differ, see files in ${BUILD_URL}/artifact/check"
    } else {
        print "${name1} and ${name2} are identical"
    }
}

def checkBranchLogs(logs, name, expected) {
    checkLogs(removeShellCall(logs), null, "logs.'${name}'", expected, null, 'expected')
}

def extractMainLogs(mainLogs, begin, end) {
    // add \n in case log starts or ends with begin/end
    def logs = '\n' + mainLogs + '\n'
    // count not allowed with older versions
    //assert logs.count("\n${begin}\n").size() == 1
    //assert logs.count("\n${end}\n").size() == 1
    // use split to count
    assert logs.split(/\n${begin}\n/).size() == 2, logs
    assert logs.split(/\n${end}\n/).size() == 2, logs

    return logs.replaceFirst(/(?s).*\n${begin}\n(.*\n)${end}\n.*/, /$1/)
}

// logs from node acquisition are not predictible (depend on node runtime availability)
def stripNodeLogs(logs, n) {
    def begin = 'STRIP_NODE_LOG_START'
    def end = 'STRIP_NODE_LOG_STOP'
    // count not allowed with older versions
    //assert logs.count("${begin}\n").size() == n
    //assert logs.count("\n${end}\n").size() == n
    // use split to count
    assert logs.split(/${begin}\n/).size() == n + 1, logs
    assert logs.split(/${end}\n/).size() == n + 1, logs

    def regexSeparated = "(?s)\\[(one|two)\\] ${begin}((?!${begin}).)*?${end}\n"
    def regexImbricated = "(?s)\\[(one|two)\\] ${begin}.*${end}\n(?!${end})"

    return logs.replaceAll(/${regexSeparated}|${regexImbricated}/, '')
}

def removeTimestamps(logs) {
    return logs.replaceAll(/(?m)^(?!<nested)(?!\[Pipeline\])(.*)\[[^\[\]]*\] (.*)$/, '$1$2')
}

def removeFilters(logs) {
    return logs.replaceAll(/(?m)^\<nested branch \[.*\]>$\n/, '')
}

def removeShellCall(logs) {
    return logs.replaceAll(/(?m)^.* Running shell script$\n/, '')
}

def expectedBranchLogs(expectedLogMap, key, branchInfo) {
    if (expectedLogMap."${key}".size() == 0 ) {
        return ''
    }

    assert expectedLogMap."${key}"[-1] == '\n'
    def expected = expectedLogMap."${key}".substring(0, expectedLogMap."${key}".size() - 1).split('\n', -1).collect{
        if (it.startsWith('<nested branch [')) {
            return it
        }
        return "${branchInfo}${it}"
    }.join('\n') + '\n'
    return expected
}

def unsortedCompare(log1, log2) {
    def sortedLog1 = log1.split('\n', -1).sort().join('\n')
    def sortedLog2 = log2.split('\n', -1).sort().join('\n')

    checkLogs(sortedLog1, null, 'sortedLog1', sortedLog2, null, 'sortedLog2')
}

def parseLogs(expectedLogMap, expectedLogMapWithoutStages, begin, end) {
    // 1/ archivelogs (for manual check)

    // archive full logs
    timestamps {
        print 'before parsing and archiving consoleText.txt'
        logparser.archiveLogsWithBranchInfo('consoleText.txt')
        print 'after parsing and archiving consoleText.txt'

        print 'before parsing and archiving branch0.txt'
        logparser.archiveLogsWithBranchInfo('branch0.txt', [filter: ['branch0']])
        print 'after parsing and archiving branch0.txt'

        print 'before parsing and archiving full.txt'
        logparser.archiveLogsWithBranchInfo('full.txt', [ hidePipeline: false, hideVT100: false ])
        print 'after parsing and archiving full.txt'

        print 'before parsing and archiving fullNoStages.txt'
        logparser.archiveLogsWithBranchInfo('fullNoStages.txt', [ showStages: false, hidePipeline: false, hideVT100: false ])
        print 'after parsing and archiving fullNoStages.txt'

        print 'before parsing and archiving branch2.txt'
        logparser.archiveLogsWithBranchInfo('branch2.txt', [filter: ['branch2']])
        print 'after parsing and archiving branch2.txt'

        print 'before parsing and archiving branch2NoNested.txt'
        logparser.archiveLogsWithBranchInfo('branch2NoNested.txt', [filter: ['branch2'], markNestedFiltered: false ])
        print 'after parsing and archiving branch2NoNested.txt'

        print 'before parsing and archiving branch2NoParent.txt'
        logparser.archiveLogsWithBranchInfo('branch2NoParent.txt', [filter: ['branch2'], showParents: false ])
        print 'after parsing and archiving branch2NoParent.txt'

        print 'before parsing and archiving branch21.txt'
        logparser.archiveLogsWithBranchInfo('branch21.txt', [filter: ['branch21']])
        print 'after parsing and archiving branch21.txt'

        print 'before parsing and archiving branch21NoParent.txt'
        logparser.archiveLogsWithBranchInfo('branch21NoParent.txt', [filter: ['branch21'], showParents: false])
        print 'after parsing and archiving branch21NoParent.txt'
    }

    // 2/ access logs programmatically using various options

    // full logs
    def fullLog = logparser.getLogsWithBranchInfo()

    // branch by branch
    def logsNoBranch = logparser.getLogsWithBranchInfo(filter:[null])
    def logsBranch0 = logparser.getLogsWithBranchInfo(filter:['branch0'])
    def logsBranch1 = logparser.getLogsWithBranchInfo(filter:['branch1'])
    def logsBranch2 = logparser.getLogsWithBranchInfo(filter:['branch2'])
    def logsBranch21 = logparser.getLogsWithBranchInfo(filter:['branch21'])
    def logsBranch22 = logparser.getLogsWithBranchInfo(filter:['branch22'])
    def logsBranch3 = logparser.getLogsWithBranchInfo(filter:['branch3'])
    def logsInit = logparser.getLogsWithBranchInfo(filter:['init'])
    def logsEmpty = logparser.getLogsWithBranchInfo(filter:['empty'])
    def logsEndl = logparser.getLogsWithBranchInfo(filter:['endl'])
    def logsMain = logparser.getLogsWithBranchInfo(filter:['main'])
    def logsBuild = logparser.getLogsWithBranchInfo(filter:['build'])
    def logsTest = logparser.getLogsWithBranchInfo(filter:['test'])
    def logsOne = logparser.getLogsWithBranchInfo(filter:['one'])
    def logsTwo = logparser.getLogsWithBranchInfo(filter:['two'])
    def logsS2b1 = logparser.getLogsWithBranchInfo(filter:['s2b1'])
    def logsS2b2 = logparser.getLogsWithBranchInfo(filter:['s2b2'])
    def logsStage1 = logparser.getLogsWithBranchInfo(filter:[ 'stage1' ])
    def logsStage2 = logparser.getLogsWithBranchInfo(filter:[ 'stage2' ])
    def logsStage3 = logparser.getLogsWithBranchInfo(filter:[ 'stage3' ])

    // multiple branches
    def logsBranchStar = logparser.getLogsWithBranchInfo(filter:[ 'branch.*' ])
    def logsS2b1S2b2 = logparser.getLogsWithBranchInfo(filter:[ 's2b1', 's2b2' ])
    def logsStar = logparser.getLogsWithBranchInfo(filter:[ '.*' ])
    def logsFullStar = logparser.getLogsWithBranchInfo(filter:[ null, '.*' ])

    // stages
    def logsNoBranchWithoutStages = logparser.getLogsWithBranchInfo(filter:[null], showStages:false)
    def logsS2b1WithoutStages = logparser.getLogsWithBranchInfo(filter:[ 's2b1' ], showStages:false)
    def logsS2b2WithoutStages = logparser.getLogsWithBranchInfo(filter:[ 's2b2' ], showStages:false)

    // other options
    def fullLogVT100 = logparser.getLogsWithBranchInfo([hideVT100:false])
    def fullLogPipeline = logparser.getLogsWithBranchInfo([hidePipeline:false])
    def fullLogPipelineVT100 = logparser.getLogsWithBranchInfo([hidePipeline:false, hideVT100:false])
    def fullLogNoNest = logparser.getLogsWithBranchInfo([markNestedFiltered:false])
    def logsBranch2NoNest = logparser.getLogsWithBranchInfo(filter:['branch2'], markNestedFiltered:false)
    def logsBranch21NoParent = logparser.getLogsWithBranchInfo(filter:['branch21'], showParents:false)
    def logsBranch2NoParent = logparser.getLogsWithBranchInfo(filter:['branch2'], showParents:false)

    // archive the raw buffers for debug
    archiveStringArtifact("dump/fullLog.txt", fullLog)
    archiveStringArtifact("dump/logsNoBranch.txt", logsNoBranch)
    archiveStringArtifact("dump/logsBranch0.txt", logsBranch0)
    archiveStringArtifact("dump/logsBranch1.txt", logsBranch1)
    archiveStringArtifact("dump/logsBranch2.txt", logsBranch2)
    archiveStringArtifact("dump/logsBranch21.txt", logsBranch21)
    archiveStringArtifact("dump/logsBranch22.txt", logsBranch22)
    archiveStringArtifact("dump/logsBranch3.txt", logsBranch3)
    archiveStringArtifact("dump/logsInit.txt", logsInit)
    archiveStringArtifact("dump/logsEmpty.txt", logsEmpty)
    archiveStringArtifact("dump/logsEndl.txt", logsEndl)
    archiveStringArtifact("dump/logsMain.txt", logsMain)
    archiveStringArtifact("dump/logsBuild.txt", logsBuild)
    archiveStringArtifact("dump/logsTest.txt", logsTest)
    archiveStringArtifact("dump/logsOne.txt", logsOne)
    archiveStringArtifact("dump/logsTwo.txt", logsTwo)
    archiveStringArtifact("dump/logsS2b1.txt", logsS2b1)
    archiveStringArtifact("dump/logsS2b2.txt", logsS2b2)
    archiveStringArtifact("dump/logsStage1.txt", logsStage1)
    archiveStringArtifact("dump/logsStage2.txt", logsStage2)
    archiveStringArtifact("dump/logsStage3.txt", logsStage3)

    archiveStringArtifact("dump/logsBranchStar.txt", logsBranchStar)
    archiveStringArtifact("dump/logsS2b1S2b2.txt", logsS2b1S2b2)
    archiveStringArtifact("dump/logsStar.txt", logsStar)
    archiveStringArtifact("dump/logsFullStar.txt", logsFullStar)

    archiveStringArtifact("dump/logsNoBranchWithoutStages.txt", logsNoBranchWithoutStages)
    archiveStringArtifact("dump/logsS2b1WithoutStages.txt", logsS2b1WithoutStages)
    archiveStringArtifact("dump/logsS2b2WithoutStages.txt", logsS2b2WithoutStages)

    archiveStringArtifact("dump/fullLogVT100.txt", fullLogVT100)
    archiveStringArtifact("dump/fullLogPipeline.txt", fullLogPipeline)
    archiveStringArtifact("dump/fullLogPipelineVT100.txt", fullLogPipelineVT100)
    archiveStringArtifact("dump/fullLogNoNest.txt", fullLogNoNest)
    archiveStringArtifact("dump/logsBranch2NoNest.txt", logsBranch2NoNest)
    archiveStringArtifact("dump/logsBranch21NoParent.txt", logsBranch21NoParent)
    archiveStringArtifact("dump/logsBranch2NoParent.txt", logsBranch2NoParent)

    // 3/ detect if timestamp is set for all pipelines
    parallel \
        'notimestamp': {
            echo ''
        },
        'timestamp': {
            timestamps {
                echo ''
            }
        }

    //print '"' + logparser.getLogsWithBranchInfo(filter:['notimestamp']) + '"'
    //print '"' + logparser.getLogsWithBranchInfo(filter:['timestamp']) + '"'
    def notimestampLog = logparser.getLogsWithBranchInfo(filter:['notimestamp'])
    def timestampLog = logparser.getLogsWithBranchInfo(filter:['timestamp'])

    def noGlobalTimestamp = notimestampLog == '[notimestamp] \n'
    def globalTimestamp = notimestampLog ==~ /\[notimestamp\] \[[^\[\]]*\] \n/
    // make sure we detected global timestamp setting correclty (if both are false something is wrong)
    assert noGlobalTimestamp != globalTimestamp, "failed to detect global timestamps setting branch\nnotimestamp log:\n'''${notimestampLog}'''"

    def localTimestamp = timestampLog ==~ /\[timestamp\] \[[^\[\]]*\] \n/
    // make sure local and global timestamp are coherent
    assert noGlobalTimestamp == localTimestamp, "failed to detect global timestamps setting branch\nnotimestamp log:\n'''${notimestampLog}'''\nlocal timestamp log:\n'''${timestampLog}'''"


    // 3.5/ strip logs accordingly
    if (globalTimestamp) {
        fullLog = removeTimestamps(fullLog)
        logsNoBranch = removeTimestamps(logsNoBranch)
        logsBranch0 = removeTimestamps(logsBranch0)
        logsBranch1 = removeTimestamps(logsBranch1)
        logsBranch2 = removeTimestamps(logsBranch2)
        logsBranch21 = removeTimestamps(logsBranch21)
        logsBranch22 = removeTimestamps(logsBranch22)
        logsBranch3 = removeTimestamps(logsBranch3)
        logsInit = removeTimestamps(logsInit)
        logsEmpty = removeTimestamps(logsEmpty)
        logsEndl = removeTimestamps(logsEndl)
        logsMain = removeTimestamps(logsMain)
        logsBuild = removeTimestamps(logsBuild)
        logsTest = removeTimestamps(logsTest)
        logsOne = removeTimestamps(logsOne)
        logsTwo = removeTimestamps(logsTwo)
        logsS2b1 = removeTimestamps(logsS2b1)
        logsS2b2 = removeTimestamps(logsS2b2)
        logsStage1 = removeTimestamps(logsStage1)
        logsStage2 = removeTimestamps(logsStage2)
        logsStage3 = removeTimestamps(logsStage3)

        logsBranchStar = removeTimestamps(logsBranchStar)
        logsS2b1S2b2 = removeTimestamps(logsS2b1S2b2)
        logsStar = removeTimestamps(logsStar)
        logsFullStar = removeTimestamps(logsFullStar)

        logsNoBranchWithoutStages = removeTimestamps(logsNoBranchWithoutStages)
        logsS2b1WithoutStages = removeTimestamps(logsS2b1WithoutStages)
        logsS2b2WithoutStages = removeTimestamps(logsS2b2WithoutStages)

        fullLogVT100 = removeTimestamps(fullLogVT100)
        fullLogPipeline = removeTimestamps(fullLogPipeline)
        fullLogPipelineVT100 = removeTimestamps(fullLogPipelineVT100)
        fullLogNoNest = removeTimestamps(fullLogNoNest)
        logsBranch2NoNest = removeTimestamps(logsBranch2NoNest)
        logsBranch21NoParent = removeTimestamps(logsBranch21NoParent)
        logsBranch2NoParent = removeTimestamps(logsBranch2NoParent)

        // archive the raw buffers for debug
        archiveStringArtifact("dump/removeTimestamps/fullLog.txt", fullLog)
        archiveStringArtifact("dump/removeTimestamps/logsNoBranch.txt", logsNoBranch)
        archiveStringArtifact("dump/removeTimestamps/logsBranch0.txt", logsBranch0)
        archiveStringArtifact("dump/removeTimestamps/logsBranch1.txt", logsBranch1)
        archiveStringArtifact("dump/removeTimestamps/logsBranch2.txt", logsBranch2)
        archiveStringArtifact("dump/removeTimestamps/logsBranch21.txt", logsBranch21)
        archiveStringArtifact("dump/removeTimestamps/logsBranch22.txt", logsBranch22)
        archiveStringArtifact("dump/removeTimestamps/logsBranch3.txt", logsBranch3)
        archiveStringArtifact("dump/removeTimestamps/logsInit.txt", logsInit)
        archiveStringArtifact("dump/removeTimestamps/logsEmpty.txt", logsEmpty)
        archiveStringArtifact("dump/removeTimestamps/logsEndl.txt", logsEndl)
        archiveStringArtifact("dump/removeTimestamps/logsMain.txt", logsMain)
        archiveStringArtifact("dump/removeTimestamps/logsBuild.txt", logsBuild)
        archiveStringArtifact("dump/removeTimestamps/logsTest.txt", logsTest)
        archiveStringArtifact("dump/removeTimestamps/logsOne.txt", logsOne)
        archiveStringArtifact("dump/removeTimestamps/logsTwo.txt", logsTwo)
        archiveStringArtifact("dump/removeTimestamps/logsS2b1.txt", logsS2b1)
        archiveStringArtifact("dump/removeTimestamps/logsS2b2.txt", logsS2b2)
        archiveStringArtifact("dump/removeTimestamps/logsStage1.txt", logsStage1)
        archiveStringArtifact("dump/removeTimestamps/logsStage2.txt", logsStage2)
        archiveStringArtifact("dump/removeTimestamps/logsStage3.txt", logsStage3)

        archiveStringArtifact("dump/removeTimestamps/logsBranchStar.txt", logsBranchStar)
        archiveStringArtifact("dump/removeTimestamps/logsS2b1S2b2.txt", logsS2b1S2b2)
        archiveStringArtifact("dump/removeTimestamps/logsStar.txt", logsStar)
        archiveStringArtifact("dump/removeTimestamps/logsFullStar.txt", logsFullStar)

        archiveStringArtifact("dump/removeTimestamps/logsNoBranchWithoutStages.txt", logsNoBranchWithoutStages)
        archiveStringArtifact("dump/removeTimestamps/logsS2b1WithoutStages.txt", logsS2b1WithoutStages)
        archiveStringArtifact("dump/removeTimestamps/logsS2b2WithoutStages.txt", logsS2b2WithoutStages)

        archiveStringArtifact("dump/removeTimestamps/fullLogVT100.txt", fullLogVT100)
        archiveStringArtifact("dump/removeTimestamps/fullLogPipeline.txt", fullLogPipeline)
        archiveStringArtifact("dump/removeTimestamps/fullLogPipelineVT100.txt", fullLogPipelineVT100)
        archiveStringArtifact("dump/removeTimestamps/fullLogNoNest.txt", fullLogNoNest)
        archiveStringArtifact("dump/removeTimestamps/logsBranch2NoNest.txt", logsBranch2NoNest)
        archiveStringArtifact("dump/removeTimestamps/logsBranch21NoParent.txt", logsBranch21NoParent)
        archiveStringArtifact("dump/removeTimestamps/logsBranch2NoParent.txt", logsBranch2NoParent)
    }

    // then strip node logs
    logsOne = stripNodeLogs(logsOne, 1)
    logsTwo = stripNodeLogs(logsTwo, 1)
    logsStar = stripNodeLogs(logsStar, 2)
    logsFullStar = stripNodeLogs(logsFullStar, 2)

    archiveStringArtifact("dump/stripNodeLogs/logsOne.txt", logsOne)
    archiveStringArtifact("dump/stripNodeLogs/logsTwo.txt", logsTwo)
    archiveStringArtifact("dump/stripNodeLogs/logsStar.txt", logsStar)
    archiveStringArtifact("dump/stripNodeLogs/logsFullStar.txt", logsFullStar)


    // 4/ check log content

    // check each branch
    checkBranchLogs(extractMainLogs(logsNoBranch, begin, end), 'null', expectedLogMap.'null')
    checkBranchLogs(logsBranch0, 'branch0', expectedBranchLogs(expectedLogMap, 'branch0', '[branch0] '))
    checkBranchLogs(logsBranch1, 'branch1',
        expectedBranchLogs(expectedLogMap, 'branch1', '[branch1] ') +
        expectedBranchLogs(expectedLogMap, 'branch2.branch1', '[branch2] [branch1] ')
    )
    checkBranchLogs(logsBranch2, 'branch2', expectedBranchLogs(expectedLogMap, 'branch2', '[branch2] '))
    checkBranchLogs(logsBranch21, 'branch21', expectedBranchLogs(expectedLogMap, 'branch2.branch21', '[branch2] [branch21] '))
    checkBranchLogs(logsBranch22, 'branch22', expectedBranchLogs(expectedLogMap, 'branch2.branch22', '[branch2] [branch22] '))
    checkBranchLogs(logsBranch3, 'branch3', expectedBranchLogs(expectedLogMap, 'branch3', '[branch3] '))
    checkBranchLogs(logsInit, 'init', expectedBranchLogs(expectedLogMap, 'init', '[init] '))
    checkBranchLogs(logsEmpty, 'empty', expectedBranchLogs(expectedLogMap, 'empty', '[empty] '))
    checkBranchLogs(logsEndl, 'endl', expectedBranchLogs(expectedLogMap, 'endl', '[endl] '))
    checkBranchLogs(logsMain, 'main', expectedBranchLogs(expectedLogMap, 'main', '[main] '))
    checkBranchLogs(logsBuild, 'build', expectedBranchLogs(expectedLogMap, 'main.build', '[main] [build] '))
    checkBranchLogs(logsTest, 'test', expectedBranchLogs(expectedLogMap, 'main.test', '[main] [test] '))
    checkBranchLogs(logsOne, 'one', expectedBranchLogs(expectedLogMap, 'one', '[one] '))
    checkBranchLogs(logsTwo, 'two', expectedBranchLogs(expectedLogMap, 'two', '[two] '))
    checkBranchLogs(logsS2b1, 's2b1', expectedBranchLogs(expectedLogMap, 'stage2.s2b1', '[stage2] [s2b1] '))
    checkBranchLogs(logsS2b2, 's2b2', expectedBranchLogs(expectedLogMap, 'stage2.s2b2', '[stage2] [s2b2] '))
    checkBranchLogs(logsStage1, 'stage1', expectedBranchLogs(expectedLogMap, 'stage1', '[stage1] '))
    checkBranchLogs(logsStage2, 'stage2', expectedBranchLogs(expectedLogMap, 'stage2', '[stage2] '))
    checkBranchLogs(logsStage3, 'stage3', expectedBranchLogs(expectedLogMap, 'stage2.stage3', '[stage2] [stage3] '))

    checkBranchLogs(extractMainLogs(logsNoBranchWithoutStages, begin, end), 'null', expectedLogMapWithoutStages.'null')
    checkBranchLogs(logsS2b1WithoutStages, 's2b1', expectedBranchLogs(expectedLogMapWithoutStages, 's2b1', '[s2b1] '))
    checkBranchLogs(logsS2b2WithoutStages, 's2b2', expectedBranchLogs(expectedLogMapWithoutStages, 's2b2', '[s2b2] '))

    // check full logs
    print 'checking fullLog contain the same lines as each branch (different order)'
    unsortedCompare(
        stripNodeLogs(extractMainLogs(fullLog, begin, end), 2),
        removeFilters(
            extractMainLogs(logsNoBranch, begin, end) +
            logsBranch0 +
            logsBranch1 +
            logsBranch2 +
            logsBranch21 +
            logsBranch22 +
            logsBranch3 +
            logsInit +
            logsEmpty +
            logsEndl +
            logsMain +
            logsBuild +
            logsTest +
            logsOne +
            logsTwo +
            logsS2b1 +
            logsS2b2 +
            logsStage1 +
            logsStage2 +
            logsStage3
        )
    )

    // check multiple branches
    print 'checking logsBranchStar contain the same lines as each branch* and main thread (different order)'
    unsortedCompare(
        logsBranchStar,
        removeFilters(
            logsBranch0 +
            logsBranch1 +
            logsBranch2 +
            logsBranch21 +
            logsBranch22 +
            logsBranch3
        )
    )
    print 'checking logsS2b1S2b2 contain the same lines as branches s2b1 and s2b2 (different order)'
    unsortedCompare(
        logsS2b1S2b2,
        removeFilters(
            logsS2b1 +
            logsS2b2
        )
    )
    print 'checking logsStar contain the same lines as each branch (different order)'
    unsortedCompare(
        logsStar,
        removeFilters(
            logsBranch0 +
            logsBranch1 +
            logsBranch2 +
            logsBranch21 +
            logsBranch22 +
            logsBranch3 +
            logsInit +
            logsEmpty +
            logsEndl +
            logsMain +
            logsBuild +
            logsTest +
            logsOne +
            logsTwo +
            logsS2b1 +
            logsS2b2 +
            logsStage1 +
            logsStage2 +
            logsStage3
        )
    )

    checkLogs(extractMainLogs(logsFullStar, begin, end), null, 'logsFullStar', stripNodeLogs(extractMainLogs(fullLog, begin, end), 2), null, 'fullLog')


    // check other options
    assert fullLogVT100 ==~ /(?s).*\x1B\[8m.*?\x1B\[0m.*/
    assert fullLog      !=~ /(?s).*\x1B\[8m.*?\x1B\[0m.*/
    assert fullLogVT100.replaceAll(/\x1B\[8m.*?\x1B\[0m/, '') == fullLog

    assert fullLogPipeline ==~ /(?s).*\[Pipeline\] .*/
    assert fullLog         !=~ /(?s).*\[Pipeline\] .*/
    checkLogs(fullLogPipeline.replaceAll(/(?m)^\[Pipeline\] .*$\n/, ''), null, 'fullLogPipeline without pipeline', fullLog, null, 'fullLog')

    assert fullLogPipelineVT100 ==~ /(?s).*\x1B\[8m.*?\x1B\[0m.*/
    checkLogs(fullLogPipelineVT100.replaceAll(/\x1B\[8m.*?\x1B\[0m/, '').replaceAll(/(?m)^\[Pipeline\] .*$\n/, ''), null, 'fullLogPipelineVT100 without pipeline', fullLog, null, 'fullLog')

    checkLogs(fullLogNoNest, null, 'fullLogNoNest', fullLog, null, 'fullLog')

    assert logsBranch2NoNest !=~ /(?s).*\<nested branch \[.*\]>.*/
    assert logsBranch2       ==~ /(?s).*\<nested branch \[.*\]>.*/
    checkLogs(logsBranch2NoNest, null, 'logsBranch2NoNest', removeFilters(logsBranch2), null, 'removeFilters(logsBranch2)')

    checkBranchLogs(logsBranch21NoParent, 'branch21', expectedBranchLogs(expectedLogMap, 'branch2.branch21', '[branch21] '))

    checkLogs(logsBranch2NoParent, null, 'logsBranch2NoParent', expectedBranchLogs(expectedLogMap, 'branch2', '[branch2] ').replace('<nested branch [branch2] [branch', '<nested branch [branch'), null, 'expected')
}

def printUrls(check) {
    def bou
    def psu
    timestamps {
        print 'before getBlueOceanUrls()'
        bou = logparser.getBlueOceanUrls()
        print 'after getBlueOceanUrls()'

        print 'before getPipelineStepsUrls()'
        psu = logparser.getPipelineStepsUrls()
        print 'after getPipelineStepsUrls()'
    }

    if (check) {
        [ bou, psu ].each {
            assert it.findAll{ it.parent == null }.size() == 1

            // check expected steps
            assert it.findAll{ it.name == 'branch0' }.size() == 1
            assert it.findAll{ it.name == 'branch1' }.size() == 2
            assert it.findAll{ it.name == 'branch2' }.size() == 1
            assert it.findAll{ it.name == 'branch21' }.size() == 1
            assert it.findAll{ it.name == 'branch22' }.size() == 1
            assert it.findAll{ it.name == 'branch3' }.size() == 1
            assert it.findAll{ it.name == 'init' }.size() == 1
            assert it.findAll{ it.name == 'empty' }.size() == 1
            assert it.findAll{ it.name == 'endl' }.size() == 1
            assert it.findAll{ it.name == 'main' }.size() == 1
            assert it.findAll{ it.name == 'build' }.size() == 1
            assert it.findAll{ it.name == 'test' }.size() == 1
            assert it.findAll{ it.name == 'one' }.size() == 1
            assert it.findAll{ it.name == 'two' }.size() == 1
            assert it.findAll{ it.name == 's2b1' }.size() == 1
            assert it.findAll{ it.name == 's2b2' }.size() == 1
            assert it.findAll{ it.name == 'timestamp' }.size() == 1
            assert it.findAll{ it.name == 'notimestamp' }.size() == 1

            // check expected stages
            assert it.findAll{ it.name == 'stage1' }.size() == 1
            assert it.findAll{ it.name == 'stage1' }.findAll{ it.stage }.size() == 1
            assert it.findAll{ it.stage }.findAll{ it.name == 'stage1' }.size() == 1

            assert it.findAll{ it.name == 'stage2' }.size() == 1
            assert it.findAll{ it.name == 'stage2' }.findAll{ it.stage }.size() == 1
            assert it.findAll{ it.stage }.findAll{ it.name == 'stage2' }.size() == 1

            assert it.findAll{ it.name == 'stage3' }.size() == 1
            assert it.findAll{ it.name == 'stage3' }.findAll{ it.stage }.size() == 1
            assert it.findAll{ it.stage }.findAll{ it.name == 'stage3' }.size() == 1

            assert it.findAll{ it.stage }.size() == 3
            assert it.findAll{ it.name != null }.size() == 3 + 19, it.findAll{ it.name != null }.collect { it.name }

            // check nested steps and stages
            [ [ 'branch21', 'branch2' ], [ 'branch22', 'branch2' ], [ 's2b1', 'stage2' ], [ 's2b2', 'stage2' ], [ 'stage1', null ] ].each{ lit ->
                def st = lit[0]
                def exp = lit[1]
                def parent = null
                def parentName = null
                def found = false
                assert it.findAll{ it.name == st }.size() == 1, "${st} ${exp}"
                it.findAll{ it.name == st }.each{ parent = it.parent }
                while(!found)  {
                    assert it.findAll{ it.id == parent }.size() == 1
                    it.findAll{ it.id == parent }.each{
                        if (it.name != null || it.parent == null) {
                            found = true
                            assert it.name == exp
                        } else {
                            parent = it.parent
                        }
                    }
                }
            }
        }
    }

    def str = ''

    str += '\n*************************\n'
    str += '* Pipelines Steps links *\n'
    str += '*************************\n'
    psu.each {
        def offset = ''
        for(def i = 0; i < it.parents.size(); i++) { offset += '    ' }
        str += "${offset}"
        if (it.stage) { str += "stage " }
        if (it.name) { str += "${it.name}" } else { str += "<step ${it.id}>" }
        str += " id=${it.id} parent=${it.parent} parents=${it.parents} children=${it.children}"
        str += "\n"
        str += "${offset}- url = ${it.url}\n"
        if (it.log) { str += "${offset}- log = ${it.log}\n" }
    }

    str += '\n********************\n'
    str += '* Blue Ocean links *\n'
    str += '********************\n'
    bou.each {
        def offset = ''
        for(def i = 0; i < it.parents.size(); i++) { offset += '    ' }
        str += "${offset}"
        if (it.stage) { str += "stage " }
        if (it.name) { str += "${it.name}" } else { str += "Start of Pipeline" }
        str += " id=${it.id} parent=${it.parent} parents=${it.parents}\n"
        str += "${offset}- url = ${it.url}\n"
        str += "${offset}- log = ${it.log}\n"
    }

    print str
}

def testLogparser() {
    // expected map of logs
    def expectedLogMap = [ 'null': '' ]

    // markers to look only at the relevant part of the logs
    def begin = 'BEGIN_TEST_LOG_BRANCH'
    def end = 'END_TEST_LOG_BRANCH'

    print begin

    runBranches(expectedLogMap)
    runSingleBranches(expectedLogMap)
    runBranchesWithManyLines(100, expectedLogMap)
    // deep copy
    def expectedLogMapWithStages = expectedLogMap.collectEntries{ k,v -> [ "$k".toString(), "$v".toString() ] }
    runStagesAndBranches(expectedLogMapWithStages, expectedLogMap)

    print end

    // add VT100 markers
    node(LABEL_LINUX) {
    }

    parseLogs(expectedLogMapWithStages, expectedLogMap, begin, end)
    printUrls(true)

    if (RUN_FULL_LOGPARSER_TEST || RUN_FULL_LOGPARSER_TEST_WITH_LOG_EDIT) {
        // test with 10 million lines (multiple hours of test, may fail if not enough heap space)
        [ 1, 10, 100, 1000, 10000 ].each {
            stage("test ${it}*1000 lines") {
                runBranchesWithManyLines(it * 1000, null)
                timestamps {
                    print 'before parsing'
                    printUrls(false)
                    if (RUN_FULL_LOGPARSER_TEST_WITH_LOG_EDIT) {
                        logparser.archiveLogsWithBranchInfo("manylines${it * 1000}.txt")
                    }
                    print 'after parsing'
                }
            }
        }
    }
}

// N threads with M groups of P lines all read logs regularly
def testManyThreads(nbthread, nbloop, nbsubloop) {

    torun = [:]
    nbthread.times {
        def id = it
        def threadName = "parallel_${id}".toString()
        torun[threadName] = {
            node(LABEL_LINUX) {
                nbloop.times {
                    def str = ''
                    def it1 = it+1
                    sh """#!/bin/bash +x
                    i=\$(( 0 ))
                    while [ \$i -lt ${nbsubloop} ]
                    do
                        echo \"thread ${threadName} / ${nbthread} loop ${it1} / ${nbloop} subloop \$i / ${nbsubloop}\"
                        i=\$(( \$i + 1 ))
                    done
                    """
                }
                logparser.archiveLogsWithBranchInfo("${threadName}.txt", [ filter : [threadName] ])
            }
        }
    }
    stage("test ${nbthread} threads x ${nbloop} x ${nbsubloop} lines") {
        timestamps {
            parallel torun
            logparser.archiveLogsWithBranchInfo("full_testManyThreads.txt")
        }
    }
}

// ===============
// = run tests   =
// ===============

testLogparser()
if (RUN_MANYTHREAD_TIMING_TEST) {
    testManyThreads(50,20,500)
}
