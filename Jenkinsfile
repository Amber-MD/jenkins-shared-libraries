pipeline {
    agent none

    options {
        skipDefaultCheckout()
        buildDiscarder(logRotator(numToKeepStr: '5'))
        timestamps()
    }

    environment {
        build_dir = 'jenkins-shared-libraries'
    }

    stages {
        stage('Checkout and stash source') {
            agent { label 'linux' }

            steps {
                dir(env.build_dir) {
                    deleteDir()
                    script {
                        env.GIT_COMMIT = checkout(scm)
                        def stuff = getLastSuccessfulCommit()
                        echo "Current build: ${stuff}"
                    }
                }
                stash includes: '**', name: 'source', useDefaultExcludes: false
            }

            post { cleanup { deleteDir() } }
        }

        stage('Unit-test the Jenkins shared pipeline') {
            agent {
                docker {
                    image 'swails/maven:latest'
                    alwaysPull true
                }
            }

            steps {
                unstash 'source'
                dir(env.build_dir) {
                    sh(label: 'Run maven', script: 'mvn -B clean test')
                }
            }

            post {
                always {
                    junit(keepLongStdio: true, testResults: "${env.build_dir}/target/surefire-reports/TEST-*.xml")
                }

                cleanup { deleteDir() }
            }
        }
    }
}

def commitHashForBuild(build) {
  def scmAction = build?.actions.find { action -> action instanceof jenkins.scm.api.SCMRevisionAction }
  return scmAction?.revision?.hash
}

def getLastSuccessfulCommit() {
  def lastSuccessfulHash = null
  def lastSuccessfulBuild = currentBuild.lastSuccessfulBuild
  if ( lastSuccessfulBuild ) {
    lastSuccessfulHash = commitHashForBuild(lastSuccessfulBuild)
  }
  return lastSuccessfulHash
}

