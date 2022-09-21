// withLogs block to get logs from any block (using logparser.withLogsWrapper)

// mark begin and end for logparser
// TODO find a better way to find the stepId ?
def _withLogMarker(Boolean begin, String name) {
    def marker = "withLogs[${begin ? 'begin' : 'end'},"
    marker += "${name}]"

    if (begin) {
        // show begining in the logs before the block
        // TODO find if it could be a technical [Pipeline] log
        print "withLogs(${name}) {"
    }

    // mark with error caught and silently ignored
    // disadvantage: appears as error in pipeline steps view and blue ocean
    // advantage: no risk of bad interpretation if someone reprints the logs
    try { error marker } catch(Error) {}

    if (!begin) {
        // show end in the logs after the block
        print "} // withLogs(${name})"
    }
}

def call(String name, Closure body) {
    this._withLogMarker(true, name)
    try {
        body()
    }
    catch(Error e) {
        print "withErrors failed with '${e.class}'"
        throw e
    }
    catch(Exception e) {
        print "withErrors failed with '${e.class}'"
        throw e
    }
    catch(e) {
        // some exceptions are not caught by previous catch
        // example 'error xxx'
        print "withErrors failed with '${e.class}'"
        throw e
    }
    finally {
        this._withLogMarker(false, name)
    }
}
