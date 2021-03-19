#!groovy

node("executor") {
    checkout scm

    def commitHash  = sh(returnStdout: true, script: 'git rev-parse HEAD | cut -c-7').trim()
    def imageTag = "${env.BUILD_NUMBER}-${commitHash}"

    def sbt = "sbt -Dsbt.log.noformat=true -Dversion=$imageTag"
    def pennsieveNexusCreds = usernamePassword(
        credentialsId: "pennsieve-nexus-ci-login",
        usernameVariable: "PENNSIEVE_NEXUS_USER",
        passwordVariable: "PENNSIEVE_NEXUS_PW"
    )

    stage("Build") {
        withCredentials([pennsieveNexusCreds]) {
            sh "$sbt clean compile"
        }
    }

    stage("Test") {
        withCredentials([pennsieveNexusCreds]) {
            try {
                sh "$sbt coverageOn test"
            } finally {
                junit '**/target/test-reports/*.xml'
            }
        }
    }

    // stage("Integration Test") {
    //     withCredentials([pennsieveNexusCreds]) {
    //         try {
    //             sh "$sbt coverageOn integration:test"
    //         } finally {
    //             junit '**/target/test-reports/*.xml'
    //         }
    //     }
    // }

    stage("Test Coverage") {
        withCredentials([pennsieveNexusCreds]) {
            sh "$sbt coverageReport"
        }
    }

    if (["master"].contains(env.BRANCH_NAME)) {
        stage("Publish Jars") {
            withCredentials([pennsieveNexusCreds]) {
                sh "$sbt common/publish"
                sh "$sbt client/publish"
            }
        }

        stage("Docker") {
            withCredentials([pennsieveNexusCreds]) {
                sh "$sbt clean server/docker"
            }

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
