// =========================
// = parser for Blue Ocean =
// =========================
// extension of logparser

//*******************************
//* GENERATE URL TO BRANCH LOGS *
//*******************************

@NonCPS
java.util.ArrayList getBlueOceanUrls(build = currentBuild) {
    // if JENKIN_URL not configured correctly, use placeholder
    def jenkinsUrl = logparser._cleanRootUrl(env.JENKINS_URL ?: '$JENKINS_URL')

    def rootUrl = null
    build.rawBuild.allActions.findAll { it.class == io.jenkins.blueocean.service.embedded.BlueOceanUrlAction }.each {
        rootUrl = logparser._cleanRootUrl(jenkinsUrl + it.blueOceanUrlObject.url)
    }
    assert rootUrl != null

    // TODO : find a better way to do get the rest url for this build ...
    def blueProvider = new io.jenkins.blueocean.service.embedded.BlueOceanRootAction.BlueOceanUIProviderImpl()
    def buildenv = build.rawBuild.getEnvironment()
    def restUrl = logparser._cleanRootUrl("${jenkinsUrl}${blueProvider.getUrlBasePrefix()}/rest${blueProvider.getLandingPagePath()}${buildenv.JOB_NAME.replace('/','/pipelines/')}/runs/${buildenv.BUILD_NUMBER}")

    def tree = logparser._getNodeTree(build)
    def ret = []

    if (logparser.verbose) {
        print "rootUrl=${rootUrl}"
        print "restUrl=${restUrl}"
        print "tree=${tree}"
    }

    tree.values().findAll{ it.parent == null || it.name != null }.each {
        def url = "${rootUrl}pipeline/${it.id}"
        def log = "${restUrl}nodes/${it.id}/log/?start=0"
        if (it.parent == null) {
            url = "${rootUrl}pipeline"
            log = "${restUrl}log/?start=0"
        }
        // if more than one stage blue ocean urls are invalid
        def parent = it.branches.size() > 0 ? it.branches[0] : null
        ret += [ [ id: it.id, name: it.name, stage: it.stage, parents: it.branches, parent: parent, url: url, log: log ] ]
    }

    if (logparser.verbose) {
        print "BlueOceanUrls=${ret}"
    }
    return ret
}
