#!groovy

node("executor") {
    checkout scm

    def commitHash  = sh(returnStdout: true, script: 'git rev-parse HEAD | cut -c-7').trim()
    def imageTag = "${env.BUILD_NUMBER}-${commitHash}"

    // sbt resolves and publishes Pennsieve libraries through AWS CodeArtifact with a
    // short-lived token from the agent's instance role (see build.sbt). Coursier
    // cache per workspace (expanded by the shell): builds sharing an agent
    // otherwise share ~/.cache/coursier and deadlock on its file locks.
    def sbt = "COURSIER_CACHE=\$WORKSPACE@coursier sbt -Dsbt.log.noformat=true -Dversion=$imageTag"

    try {
        stage("Build") {
            sh "$sbt clean +compile"
        }

        stage("Test") {
            try {
                sh "$sbt coverageOn +test"
            } finally {
                junit '**/target/test-reports/*.xml'
            }
        }

        // stage("Integration Test") {
        //     try {
        //         sh "$sbt coverageOn integration:test"
        //     } finally {
        //         junit '**/target/test-reports/*.xml'
        //     }
        // }

        stage("Test Coverage") {
            sh "$sbt coverageReport"
        }

        if (env.BRANCH_NAME == 'main') {
            stage("Publish Jars") {
                sh "$sbt +common/publish"
                sh "$sbt +client/publish"
            }

            stage("Docker") {
                sh "$sbt clean server/docker"

                sh "docker tag pennsieve/doi-service:latest pennsieve/doi-service:$imageTag"
                sh "docker push pennsieve/doi-service:latest"
                sh "docker push pennsieve/doi-service:$imageTag"
            }

            stage("Deploy") {
                build job: "service-deploy/pennsieve-non-prod/us-east-1/dev-vpc-use1/dev/doi-service",
                parameters: [
                    string(name: 'IMAGE_TAG', value: imageTag),
                    string(name: 'TERRAFORM_ACTION', value: 'apply')
                ]
            }
        }
    }
    catch (e) {
        currentBuild.result = 'FAILED'
        throw e
    }
    finally {
        notifyBuild(currentBuild.result)
    }
}

// Slack build status notifications
def notifyBuild(String buildStatus) {
  // Build status of null means successful
  buildStatus = buildStatus ?: 'SUCCESS'

  def authorName = sh(returnStdout: true, script: 'git --no-pager show --format="%an" --no-patch').trim()
  def color
  def message = "${buildStatus}: Job '${env.JOB_NAME} [${env.BUILD_NUMBER}]' (${env.BUILD_URL}) by ${authorName}"

  if (buildStatus == 'SUCCESS') {
    color = '#00FF00' // Green
  } else {
    color = '#FF0000' // Red
  }

  slackSend(color: color, message: message)
}
