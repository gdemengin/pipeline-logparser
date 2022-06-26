import jenkins.model.*

def jenkinsLocationConfiguration = JenkinsLocationConfiguration.get()
jenkinsLocationConfiguration.setUrl('http://ci.jenkins.internal:8080/')
jenkinsLocationConfiguration.setAdminAddress('Jenkins Admin <admin@ci.jenkins.internal>')
jenkinsLocationConfiguration.save()
