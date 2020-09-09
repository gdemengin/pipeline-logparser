// sample pipeline to test https://github.com/gdemengin/pipeline-logparser/ "Global Pipeline Library" logparser


// import logparser library
@Library('pipeline-logparser@1.2') _

// ===============
// = constants   =
// ===============

// set to to true to run extra long tests
// (multiple hours + may fail if not enough heap)
RUN_FULL_LOGPARSER_TEST = false

// =============
// = globals   =
// =============

// uncomment if needed
// logparser.setVerbose(true)

// true when testing old version with old log format
@groovy.transform.Field
newLogFormat = true

newLogFormat = logparser.logHasNewFormat()

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
        expectedLogMap.'branch2' += '[ filtered XX bytes of logs for nested branches: branch2.branch1 branch2.branch21 branch2.branch22 ] (...)\n'
    }

    // branch with no logs
    expectedLogMap.'branch3' = ''
    branches.branch3 = {
        def k = 0
        k += 1
    }

    // run branches
    parallel branches
    expectedLogMap.'null' += '[ filtered XX bytes of logs for nested branches: branch0 branch1 branch2 branch2.branch1 branch2.branch21 branch2.branch22 ] (...)\n'

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
        expectedLogMap.'main' += '[ filtered XX bytes of logs for nested branches: main.build main.test ] (...)\n'
    }
    expectedLogMap.'null' += '[ filtered XX bytes of logs for nested branches: init main.build main.test ] (...)\n'
}

def runBranchesWithManyLines(nblines, expectedLogMap) {
    // run branches with manylines and making sure lines are mixed between branches
    // without technical pipeline pieces of logs between each switch of branch in the logs
    def makelogs = { b, t1, t2 ->
        def out = ''
        for (def i = 0; i < nblines; i++) {
            out += "line $i\n"
        }
        print "starting ${b}"
        expectedLogMap."${b}" += "starting ${b}\n"
        sleep t1
        expectedLogMap."${b}" += "Sleeping for ${t1} sec\n"
        print out
        expectedLogMap."${b}" += out + '\n'
        sleep t2
        expectedLogMap."${b}" += "Sleeping for ${t2} sec\n"
        print "ending ${b}"
        expectedLogMap."${b}" += "ending ${b}\n"
   }

   def line = "testing branches with ${nblines}"
   print line
   expectedLogMap.'null' += line + '\n'

    expectedLogMap.'one' = ''
    expectedLogMap.'two' = ''

    parallel one: {
        makelogs('one', 1, 2)
    }, two: {
        makelogs('two', 2, 1)
    }
    expectedLogMap.'null' += '[ filtered XX bytes of logs for nested branches: one two ] (...)\n'
}

def runStagesAndBranches(expectedLogMap, expectedLogMapWithStages) {
    def line = 'testing stages'
    print line
    expectedLogMap.'null' += line + '\n'
    expectedLogMapWithStages.'null' += line + '\n'

    expectedLogMapWithStages.'logparser-stage1' = ''
    stage('logparser-stage1') {
        line='in stage1'
        echo line
        expectedLogMap.'null' += line + '\n'
        expectedLogMapWithStages.'logparser-stage1' += line + '\n'
    }

    expectedLogMapWithStages.'logparser-stage2' = ''
    stage('logparser-stage2') {
        expectedLogMap.'s2b1' = ''
        expectedLogMap.'s2b2' = ''
        expectedLogMapWithStages.'logparser-stage2.s2b1' = ''
        expectedLogMapWithStages.'logparser-stage2.s2b2' = ''
        expectedLogMapWithStages.'logparser-stage2.logparser-stage3' = ''

        stage('logparser-stage3') {
            line='in stage2.stage3'
            echo line
            expectedLogMap.'null' += line + '\n'
            expectedLogMapWithStages.'logparser-stage2.logparser-stage3' += line + '\n'
        }

        parallel 's2b1': {
            line='in stage2.s2b1'
            echo line
            expectedLogMap.'s2b1' += line + '\n'
            expectedLogMapWithStages.'logparser-stage2.s2b1' += line + '\n'
        }, 's2b2': {
            line='in stage2.s2b2'
            echo line
            expectedLogMap.'s2b2' += line + '\n'
            expectedLogMapWithStages.'logparser-stage2.s2b2' += line + '\n'
        }

        expectedLogMap.'null' += '[ filtered XX bytes of logs for nested branches: s2b1 s2b2 ] (...)\n'
        expectedLogMapWithStages.'logparser-stage2' += '[ filtered XX bytes of logs for nested branches: logparser-stage2.logparser-stage3 logparser-stage2.s2b1 logparser-stage2.s2b2 ] (...)\n'
    }
    expectedLogMapWithStages.'null' += '[ filtered XX bytes of logs for nested branches: logparser-stage1 logparser-stage2.logparser-stage3 logparser-stage2.s2b1 logparser-stage2.s2b2 ] (...)\n'
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
    if (newLogFormat) {
        // expected do not contain the actual number of bytes to keep it simple
        def editedLogs = logs.replaceAll(/(?m)^\[ filtered [0-9]* bytes of logs/, '[ filtered XX bytes of logs')
        checkLogs(logs, editedLogs, "logs.'${name}'", expected, null, 'expected')
    } else {
        // old version does not generate filtering markups
        def editedExpected = expected.replaceAll(/(?m)^\[ filtered XX bytes of logs.*$\n/, '')
        checkLogs(logs, null, "logs.'${name}'", expected, editedExpected, 'expected')
    }
}

def extractMainLogs(mainLogs, begin, end) {
    // count not allowed with older versions
    //assert mainLogs.count("\n${begin}\n").size() == 1
    //assert mainLogs.count("\n${end}\n").size() == 1
    // use split to count
    assert mainLogs.split("\n${begin}\n").size() == 2
    assert mainLogs.split("\n${end}\n").size() == 2
    return mainLogs.replaceFirst(/(?s).*\n${begin}\n(.*\n)${end}\n.*/, /$1/)
}

def removeFilters(logs) {
    return logs.replaceAll(/(?m)^\[ filtered [0-9]* bytes of logs.*$\n/, '')
}

def expectedBranchLogs(expectedLogMap, key, branchInfo) {
    if (expectedLogMap."${key}".size() == 0 ) {
        return ''
    }

    assert expectedLogMap."${key}"[-1] == '\n'
    def expected = expectedLogMap."${key}".substring(0, expectedLogMap."${key}".size() - 1).split('\n', -1).collect{
        if (it.startsWith('[ filtered XX bytes of logs')) {
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

    // sleep 1s and use echo to flush logs before to call logparser
    // might not be enough
    // TODO: find a better way to make sure the log is complete (may be print a marker in the log and wait for it to appear ??)
    sleep 1
    echo ''

    // 1/ archivelogs (for manual check)

    // archive full logs
    timestamps {
        print 'before parsing and archiving consoleText.txt'
        logparser.archiveLogsWithBranchInfo('consoleText.txt')
        print 'after parsing and archiving consoleText.txt'

        print 'before parsing and archiving branch0.txt'
        logparser.archiveLogsWithBranchInfo('branch0.txt', [filter: ['branch0']])
        print 'after parsing and archiving branch0.txt'
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
    def logsMain = logparser.getLogsWithBranchInfo(filter:['main'])
    def logsBuild = logparser.getLogsWithBranchInfo(filter:['build'])
    def logsTest = logparser.getLogsWithBranchInfo(filter:['test'])
    def logsOne = logparser.getLogsWithBranchInfo(filter:['one'])
    def logsTwo = logparser.getLogsWithBranchInfo(filter:['two'])
    def logsS2b1 = logparser.getLogsWithBranchInfo(filter:['s2b1'])
    def logsS2b2 = logparser.getLogsWithBranchInfo(filter:['s2b2'])

    // multiple branches
    def logsBranchStar = logparser.getLogsWithBranchInfo(filter:[ 'branch.*' ])
    def logsS2b1S2b2 = logparser.getLogsWithBranchInfo(filter:[ 's2b1', 's2b2' ])
    def logsStar = logparser.getLogsWithBranchInfo(filter:[ '.*' ])
    def logsFullStar = logparser.getLogsWithBranchInfo(filter:[ null, '.*' ])

    // stages
    def logsNoBranchWithStages = logparser.getLogsWithBranchInfo(filter:[null], showStages:true)
    def logsS2b1WithStages = logparser.getLogsWithBranchInfo(filter:[ 's2b1' ], showStages:true)
    def logsS2b2WithStages = logparser.getLogsWithBranchInfo(filter:[ 's2b2' ], showStages:true)
    def logsStage1 = logparser.getLogsWithBranchInfo(filter:[ 'logparser-stage1' ], showStages:true)
    def logsStage2 = logparser.getLogsWithBranchInfo(filter:[ 'logparser-stage2' ], showStages:true)
    def logsStage3 = logparser.getLogsWithBranchInfo(filter:[ 'logparser-stage3' ], showStages:true)

    // other options
    def fullLogVT100 = logparser.getLogsWithBranchInfo([hideVT100:false])
    def fullLogPipeline = logparser.getLogsWithBranchInfo([hidePipeline:false])
    def fullLogPipelineVT100 = logparser.getLogsWithBranchInfo([hidePipeline:false, hideVT100:false])
    def fullLogNoNest = logparser.getLogsWithBranchInfo([markNestedFiltered:false])
    def logsBranch2NoNest = logparser.getLogsWithBranchInfo(filter:['branch2'], markNestedFiltered:false)
    def logsBranch21NoParent = logparser.getLogsWithBranchInfo(filter:['branch21'], showParents:false)

    // 3/ check log content

    // check each branch
    checkBranchLogs(extractMainLogs(logsNoBranch, begin, end), 'null', expectedLogMap.'null')
    checkBranchLogs(logsBranch0, 'branch0', expectedBranchLogs(expectedLogMap, 'branch0', '[branch0] '))
    checkBranchLogs(logsBranch1, 'branch1',
        expectedBranchLogs(expectedLogMap, 'branch1', '[branch1] ') +
        expectedBranchLogs(expectedLogMap, 'branch2.branch1', newLogFormat ? '[branch2] [branch1] ' : '[branch1] ')
    )
    checkBranchLogs(logsBranch2, 'branch2', expectedBranchLogs(expectedLogMap, 'branch2', '[branch2] '))
    checkBranchLogs(logsBranch21, 'branch21', expectedBranchLogs(expectedLogMap, 'branch2.branch21', newLogFormat ? '[branch2] [branch21] ' : '[branch21] '))
    checkBranchLogs(logsBranch22, 'branch22', expectedBranchLogs(expectedLogMap, 'branch2.branch22', newLogFormat ? '[branch2] [branch22] ' : '[branch22] '))
    checkBranchLogs(logsBranch3, 'branch3', expectedBranchLogs(expectedLogMap, 'branch3', '[branch3] '))
    checkBranchLogs(logsInit, 'init', expectedBranchLogs(expectedLogMap, 'init', '[init] '))
    checkBranchLogs(logsMain, 'main', expectedBranchLogs(expectedLogMap, 'main', '[main] '))
    checkBranchLogs(logsBuild, 'build', expectedBranchLogs(expectedLogMap, 'main.build', newLogFormat ? '[main] [build] ' : '[build] '))
    checkBranchLogs(logsTest, 'test', expectedBranchLogs(expectedLogMap, 'main.test', newLogFormat ? '[main] [test] ' : '[test] '))
    checkBranchLogs(logsOne, 'one', expectedBranchLogs(expectedLogMap, 'one', '[one] '))
    checkBranchLogs(logsTwo, 'two', expectedBranchLogs(expectedLogMap, 'two', '[two] '))
    checkBranchLogs(logsS2b1, 's2b1', expectedBranchLogs(expectedLogMap, 's2b1', '[s2b1] '))
    checkBranchLogs(logsS2b2, 's2b2', expectedBranchLogs(expectedLogMap, 's2b2', '[s2b2] '))

    if (newLogFormat) {
        checkBranchLogs(extractMainLogs(logsNoBranchWithStages, begin, end), 'null', expectedLogMapWithStages.'null')
        checkBranchLogs(logsS2b1WithStages, 's2b1', expectedBranchLogs(expectedLogMapWithStages, 'logparser-stage2.s2b1', '[logparser-stage2] [s2b1] '))
        checkBranchLogs(logsS2b2WithStages, 's2b2', expectedBranchLogs(expectedLogMapWithStages, 'logparser-stage2.s2b2', '[logparser-stage2] [s2b2] '))
        checkBranchLogs(logsStage1, 'logparser-stage1', expectedBranchLogs(expectedLogMapWithStages, 'logparser-stage1', '[logparser-stage1] '))
        checkBranchLogs(logsStage2, 'logparser-stage2', expectedBranchLogs(expectedLogMapWithStages, 'logparser-stage2', '[logparser-stage2] '))
        checkBranchLogs(logsStage3, 'logparser-stage3', expectedBranchLogs(expectedLogMapWithStages, 'logparser-stage2.logparser-stage3', '[logparser-stage2] [logparser-stage3] '))
    }

    // check full logs
    print 'checking fullLog contain the same lines as each branch (different order)'
    unsortedCompare(
        extractMainLogs(fullLog, begin, end),
        removeFilters(
            extractMainLogs(logsNoBranch, begin, end) +
            logsBranch0 +
            logsBranch1 +
            logsBranch2 +
            logsBranch21 +
            logsBranch22 +
            logsBranch3 +
            logsInit +
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
            logsMain +
            logsBuild +
            logsTest +
            logsOne +
            logsTwo +
            logsS2b1 +
            logsS2b2
        )
    )

    checkLogs(extractMainLogs(logsFullStar, begin, end), null, 'logsFullStar', extractMainLogs(fullLog, begin, end), null, 'fullLog')


    // check other options
    // this one might fail when job started by timer
    // TODO retest that
    // TODO: test it with repeatable call
    // assert fullLogVT100 ==~ /(?s).*\x1B\[8m.*?\x1B\[0m.*/
    assert fullLog      !=~ /(?s).*\x1B\[8m.*?\x1B\[0m.*/
    assert fullLogVT100.replaceAll(/\x1B\[8m.*?\x1B\[0m/, '') == fullLog

    assert fullLogPipeline ==~ /(?s).*\[Pipeline\] .*/
    assert fullLog         !=~ /(?s).*\[Pipeline\] .*/
    checkLogs(fullLogPipeline.replaceAll(/(?m)^\[Pipeline\] .*$\n/, ''), null, 'fullLogPipeline without pipeline', fullLog, null, 'fullLog')

    assert fullLogPipelineVT100 ==~ /(?s).*\x1B\[8m.*?\x1B\[0m.*/
    checkLogs(fullLogPipelineVT100.replaceAll(/\x1B\[8m.*?\x1B\[0m/, '').replaceAll(/(?m)^\[Pipeline\] .*$\n/, ''), null, 'fullLogPipelineVT100 without pipeline', fullLog, null, 'fullLog')

    checkLogs(fullLogNoNest, null, 'fullLogNoNest', fullLog, null, 'fullLog')

    if (newLogFormat) {
        assert logsBranch2NoNest !=~ /(?s).*\[ filtered [0-9]* bytes of logs .*/
        assert logsBranch2       ==~ /(?s).*\[ filtered [0-9]* bytes of logs .*/
        checkLogs(logsBranch2NoNest, null, 'logsBranch2NoNest', removeFilters(logsBranch2), null, 'removeFilters(logsBranch2)')
    } else {
        checkLogs(logsBranch2NoNest, null, 'logsBranch2NoNest', logsBranch2, null, 'logsBranch2')
    }

    checkBranchLogs(logsBranch21NoParent, 'branch21', expectedBranchLogs(expectedLogMap, 'branch2.branch21', '[branch21] '))
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

    parseLogs(expectedLogMap, expectedLogMapWithStages, begin, end)

    if (RUN_FULL_LOGPARSER_TEST) {
        // test with 10 million lines (multiple hours of test, may fail if not enough heap space)
        [ 1, 10, 100, 1000, 10000 ].each {
            stage("test ${it}*1000 lines") {
                def tmpLogMap = [ 'null': '' ]
                runBranchesWithManyLines(it * 1000, tmpLogMap)
                timestamps {
                    print 'before parsing'
                    logparser.archiveLogsWithBranchInfo("manylines${it * 1000}.txt")
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
