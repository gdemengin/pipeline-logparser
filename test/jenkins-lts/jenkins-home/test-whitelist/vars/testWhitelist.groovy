// blacklisted functions needed by tests

List<java.util.LinkedHashMap> getPlugins() {
    return Jenkins.instance.pluginManager.plugins.collect {
        [
            displayName: it.getDisplayName(),
            shortName: it.getShortName(),
            version: it.getVersion()
        ]
    }
}

String getVersion() {
    return "${Jenkins.instance.getVersion()}"
}

// TODO: transfer archiveArtifactBuffer here (not useful in lib anymore)
