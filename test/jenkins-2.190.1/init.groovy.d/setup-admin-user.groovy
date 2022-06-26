import jenkins.model.*
import hudson.security.*

println "--> Setting up admin user"

def adminUsername = System.getenv("JENKINS_ADMIN_USERNAME")
def adminPassword = System.getenv("JENKINS_ADMIN_PASSWORD")

assert adminPassword != null : "JENKINS_ADMIN_USERNAME env variable must be set"
assert adminPassword != null : "No JENKINS_ADMIN_PASSWORD env variable must be set"

def hudsonRealm = new HudsonPrivateSecurityRealm(false)
hudsonRealm.createAccount(adminUsername, adminPassword)
Jenkins.instance.setSecurityRealm(hudsonRealm)

def authorizationStrategy = new FullControlOnceLoggedInAuthorizationStrategy()
authorizationStrategy.setAllowAnonymousRead(false)
Jenkins.instance.setAuthorizationStrategy(authorizationStrategy)

Jenkins.instance.save()
