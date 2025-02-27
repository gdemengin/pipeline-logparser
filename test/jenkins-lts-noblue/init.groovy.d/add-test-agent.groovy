import hudson.model.*
import jenkins.model.*
import hudson.slaves.*
import org.jenkinsci.plugins.scriptsecurity.scripts.*
import org.jenkinsci.plugins.scriptsecurity.scripts.languages.*

// use master as test-agent but declare it as a separate host

String script = '/manage_jenkins.sh local_agent /home/test-agent'
ComputerLauncher launcher = new CommandLauncher(script)

ScriptApproval.get().approveScript(
    new ScriptApproval.PendingScript(
        script,
        SystemCommandLanguage.get(),
        ApprovalContext.create()
    ).getHash()
)

Slave agent = new DumbSlave(
    "test-agent",
    "/home/test-agent",
    launcher
)
agent.nodeDescription = "Agent on master for test"
agent.numExecutors = 4
agent.labelString = "test-agent"
agent.mode = Node.Mode.NORMAL
agent.retentionStrategy = new RetentionStrategy.Always()

Jenkins.instance.addNode(agent)
