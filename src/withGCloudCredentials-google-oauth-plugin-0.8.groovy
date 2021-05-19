import hudson.util.Secret
import com.cloudbees.plugins.credentials.CredentialsProvider
import com.google.jenkins.plugins.credentials.oauth.GoogleRobotPrivateKeyCredentials
import com.google.jenkins.plugins.credentials.oauth.GoogleOAuth2ScopeRequirement

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
private def writeKeyFile(jsonKey) {
    def json = Secret.decrypt(new String(jsonKey.getPlainData())).getPlainText()

    writeFile encoding: 'UTF-8', file: '.auth/gcloud.json', text: json
    return pwd() + "/.auth/gcloud.json"
}

def call(projectId, credentialsId = null, body) {
  if (!credentialsId) {
    credentialsId = projectId
  }
  def serviceAccount = getCredentials(credentialsId).getServiceAccountConfig();
  def keyFile = writeKeyFile(serviceAccount.getSecretJsonKey())
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
