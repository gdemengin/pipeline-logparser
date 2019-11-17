// sample pipeline to test https://github.com/gdemengin/pipeline-logparser/ "Global Pipeline Library" logparser


// import logparser library
@Library('pipeline-logparser@blue') _


properties([
    parameters([
        booleanParam(defaultValue: false, description: '''set to to true to run extra long tests (multiple hours + may fail if not enough heap)''', name: 'FULL_LOGPARSER_TEST'),
        booleanParam(defaultValue: false, description: 'FULL_LOGPARSER_TEST + even more aggressive: with log editing', name: 'FULL_LOGPARSER_TEST_WITH_LOG_EDIT')
    ])
])

// ===============
// = constants   =
// ===============

LABEL_LINUX='linux'

// set to to true to run extra long tests
// (multiple hours + may fail if not enough heap)
RUN_FULL_LOGPARSER_TEST = params.FULL_LOGPARSER_TEST
// even more aggressive: with log editing
RUN_FULL_LOGPARSER_TEST_WITH_LOG_EDIT = params.FULL_LOGPARSER_TEST_WITH_LOG_EDIT

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

def runStagesAndBranches(expectedLogMap, expectedLogMapWithStages) {
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

def checkLogs(log1, editedLog1, name1, log2, editedLog2, name2) {
    def tocmp1 = editedLog1 == null ? log1 : editedLog1
    def tocmp2 = editedLog2 == null ? log2 : editedLog2
    if (tocmp1 != tocmp2) {
        // TODO: print side by side differences
        print "${name1} = '''\\\n${log1}'''"
        if (editedLog1 != null && log1 != editedLog1) {
            print "${name1} (edited) ='''\\\n${editedLog1}'''"
        }
        print "${name2} = '''\\\n${log2}'''"
        if (editedLog2 != null && log2 != editedLog2) {
            print "${name2} (edited) ='''\\\n${editedLog2}'''"
        }
        error "${name1} and ${name2} differ"
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
    assert logs.split("\n${begin}\n").size() == 2, logs
    assert logs.split("\n${end}\n").size() == 2, logs

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
    assert logs.split("${begin}\n").size() == n + 1, logs
    assert logs.split("${end}\n").size() == n + 1, logs

    def regexSeparated = "(?s)\\[(one|two)\\] ${begin}((?!${begin}).)*?${end}\n"
    def regexImbricated = "(?s)\\[(one|two)\\] ${begin}.*${end}\n(?!${end})"

    return logs.replaceAll(/${regexSeparated}|${regexImbricated}/, '')
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

def parseLogs(expectedLogMap, expectedLogMapWithStages, begin, end) {
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
        logparser.archiveLogsWithBranchInfo('full.txt', [ showStages: true, hidePipeline: false, hideVT100: false ])
        print 'after parsing and archiving full.txt'

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
    def logsOne = stripNodeLogs(logparser.getLogsWithBranchInfo(filter:['one']), 1)
    def logsTwo = stripNodeLogs(logparser.getLogsWithBranchInfo(filter:['two']), 1)
    def logsS2b1 = logparser.getLogsWithBranchInfo(filter:['s2b1'])
    def logsS2b2 = logparser.getLogsWithBranchInfo(filter:['s2b2'])

    // multiple branches
    def logsBranchStar = logparser.getLogsWithBranchInfo(filter:[ 'branch.*' ])
    def logsS2b1S2b2 = logparser.getLogsWithBranchInfo(filter:[ 's2b1', 's2b2' ])
    def logsStar = stripNodeLogs(logparser.getLogsWithBranchInfo(filter:[ '.*' ]), 2)
    def logsFullStar = stripNodeLogs(logparser.getLogsWithBranchInfo(filter:[ null, '.*' ]), 2)

    // stages
    def logsNoBranchWithStages = logparser.getLogsWithBranchInfo(filter:[null], showStages:true)
    def logsS2b1WithStages = logparser.getLogsWithBranchInfo(filter:[ 's2b1' ], showStages:true)
    def logsS2b2WithStages = logparser.getLogsWithBranchInfo(filter:[ 's2b2' ], showStages:true)
    def logsStage1 = logparser.getLogsWithBranchInfo(filter:[ 'stage1' ], showStages:true)
    def logsStage2 = logparser.getLogsWithBranchInfo(filter:[ 'stage2' ], showStages:true)
    def logsStage3 = logparser.getLogsWithBranchInfo(filter:[ 'stage3' ], showStages:true)

    // other options
    def fullLogVT100 = logparser.getLogsWithBranchInfo([hideVT100:false])
    def fullLogPipeline = logparser.getLogsWithBranchInfo([hidePipeline:false])
    def fullLogPipelineVT100 = logparser.getLogsWithBranchInfo([hidePipeline:false, hideVT100:false])
    def fullLogNoNest = logparser.getLogsWithBranchInfo([markNestedFiltered:false])
    def logsBranch2NoNest = logparser.getLogsWithBranchInfo(filter:['branch2'], markNestedFiltered:false)
    def logsBranch21NoParent = logparser.getLogsWithBranchInfo(filter:['branch21'], showParents:false)
    def logsBranch2NoParent = logparser.getLogsWithBranchInfo(filter:['branch2'], showParents:false)

    // 3/ check log content

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
    checkBranchLogs(logsS2b1, 's2b1', expectedBranchLogs(expectedLogMap, 's2b1', '[s2b1] '))
    checkBranchLogs(logsS2b2, 's2b2', expectedBranchLogs(expectedLogMap, 's2b2', '[s2b2] '))

    checkBranchLogs(extractMainLogs(logsNoBranchWithStages, begin, end), 'null', expectedLogMapWithStages.'null')
    checkBranchLogs(logsS2b1WithStages, 's2b1', expectedBranchLogs(expectedLogMapWithStages, 'stage2.s2b1', '[stage2] [s2b1] '))
    checkBranchLogs(logsS2b2WithStages, 's2b2', expectedBranchLogs(expectedLogMapWithStages, 'stage2.s2b2', '[stage2] [s2b2] '))
    checkBranchLogs(logsStage1, 'stage1', expectedBranchLogs(expectedLogMapWithStages, 'stage1', '[stage1] '))
    checkBranchLogs(logsStage2, 'stage2', expectedBranchLogs(expectedLogMapWithStages, 'stage2', '[stage2] '))
    checkBranchLogs(logsStage3, 'stage3', expectedBranchLogs(expectedLogMapWithStages, 'stage2.stage3', '[stage2] [stage3] '))

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
            logsS2b2
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
            logsS2b2
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
            assert it.findAll{ it.name != null }.size() == 3 + 17, it.findAll{ it.name != null }.collect { it.name }

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
    runStagesAndBranches(expectedLogMap, expectedLogMapWithStages)

    print end

    // add VT100 markers
    node(LABEL_LINUX) {
    }

    parseLogs(expectedLogMap, expectedLogMapWithStages, begin, end)
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

// ===============
// = run tests   =
// ===============

testLogparser()
