import com.cloudbees.plugins.credentials.*;
import com.cloudbees.plugins.credentials.domains.Domain;
import org.jenkinsci.plugins.plaincredentials.impl.FileCredentialsImpl;
import java.nio.file.*;
import org.csanchez.jenkins.plugins.kubernetes.*
import jenkins.model.*

Path fileLocation = Paths.get("/kubeconfig/config.kube");

def secretBytes = SecretBytes.fromBytes(Files.readAllBytes(fileLocation))
def credentials = new FileCredentialsImpl(CredentialsScope.GLOBAL, 'kube', 'kubernetes', 'kube', secretBytes)

SystemCredentialsProvider.instance.store.addCredentials(Domain.global(), credentials)


def instance = Jenkins.getInstance()

kc = new KubernetesCloud('kube')
kc.setCredentialsId('kube')
jenkinsUrl = System.getenv("JENKINS_URL")
jenkinsUrl && kc.setJenkinsUrl(jenkinsUrl)
 println "Adding k8s cloud: ${kc.getDisplayName()}"
instance.clouds.add(kc)
instance.save()
