import java.io.File
import hudson.FilePath
import hudson.util.Secret
import jenkins.model.Jenkins
import com.cloudbees.plugins.credentials.CredentialsProvider
import com.google.jenkins.plugins.credentials.oauth.GoogleRobotPrivateKeyCredentials
import com.google.jenkins.plugins.credentials.oauth.GoogleOAuth2ScopeRequirement
import org.apache.commons.io.IOUtils

@NonCPS
private def getCredentials(credentialsId) {
    def build = currentBuild.rawBuild
    CredentialsProvider.findCredentialById(
      credentialsId,
      GoogleRobotPrivateKeyCredentials.class,
      build,
      new GoogleOAuth2ScopeRequirement()  {
            @Override
            public Collection<String> getScopes() {
              return null;
            }
          }
      );
}
private def copyLocalKeyFile(keyFilePath) {
    def channel = Jenkins.getInstance().getComputer('master')
    remoteKeyFile = new FilePath(channel, keyFilePath)
    json = Secret.decrypt(remoteKeyFile.readToString()).getPlainText()

    writeFile encoding: 'UTF-8', file: '.auth/gcloud.json', text: json
    return pwd() + "/.auth/gcloud.json"
}

def call(projectId, credentialsId = null, body) {
  if (!credentialsId) {
    credentialsId = projectId
  }
  def serviceAccount = getCredentials(credentialsId).getServiceAccountConfig();
  def keyFile = copyLocalKeyFile(serviceAccount.getJsonKeyFile())
  def accountId = serviceAccount.getAccountId()
  def gcloud = tool 'gcloud'

  withEnv(["PATH+GCLOUD=${gcloud}/bin","CLOUDSDK_CORE_PROJECT=$projectId","GOOGLE_APPLICATION_CREDENTIALS=$keyFile"]) {
    sh "gcloud auth activate-service-account $accountId --key-file=$keyFile"

    try {
      body()
    } finally {
      sh "gcloud auth revoke $accountId && rm $keyFile"
    }
  }
}
