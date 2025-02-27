// dump plugins list and jenkins version to be able to reinstall the exact same one
import jenkins.model.*
import java.io.File

List<java.util.LinkedHashMap> plugins() {
    return Jenkins.instance.pluginManager.plugins.collect {
        [
            displayName: it.getDisplayName(),
            shortName: it.getShortName(),
            version: it.getVersion()
        ]
    }
}

def dumpVersion(target) {
    def instanceVersion = "${Jenkins.instance.getVersion()}"
    def versionStr = "Jenkins Instance Version : ${instanceVersion}"
    println versionStr

    def plugins = plugins()
    plugins.sort{ it['shortName'] }
    def pluginsVersionStr = "Plugins : \n${plugins.collect{ "\t${it.displayName} (${it.shortName}) v${it.version}" }.join('\n')}"
    println pluginsVersionStr

    def pluginsShortVersion = plugins.collect{ "${it.shortName}:${it.version}" }.join('\n')

    new File("${target}/version").write(instanceVersion)
    new File("${target}/plugins.txt").write(pluginsShortVersion)
    // human readable
    new File("${target}/versions.txt").write("${versionStr}\n${pluginsVersionStr}")
}

// dump in workspace
def workspace = System.getenv('GITHUB_WORKSPACE')
dumpVersion("${workspace}/.version")
