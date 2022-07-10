import org.jenkinsci.plugins.workflow.libs.*
import jenkins.plugins.git.GitSCMSource

def workspace = System.getenv('GITHUB_WORKSPACE')
assert workspace != null

def lib = new LibraryConfiguration(
    "pipeline-logparser",
    new SCMSourceRetriever(new GitSCMSource("file://${workspace}/.tmp-test/.git"))
)

gl = GlobalLibraries.get()
gl.libraries.add(lib)

