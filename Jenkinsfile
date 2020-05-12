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
                    script {
                        env.GIT_COMMIT = checkout(scm)
                    }
                }
                stash includes: '**', name: 'source', useDefaultExcludes: false
            }

            post { cleanup { deleteDir() } }
        }

        stage('Unit-test the Jenkins shared pipeline') {
            agent {
                docker {
                    image 'adoptopenjdk/maven-openjdk11:latest'
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
                    junit(keepLongStdio: true, testResults: 'target/surefire-reports/TEST-*.xml')
                }
            }
        }
    }
}