import org.jenkinsci.plugins.workflow.libs.*
import jenkins.plugins.git.GitSCMSource

def lib = new LibraryConfiguration(
    "test-whitelist",
    new SCMSourceRetriever(new GitSCMSource('file:///var/jenkins_home/test-whitelist/.git'))
)

gl = GlobalLibraries.get()
gl.libraries.add(lib)

