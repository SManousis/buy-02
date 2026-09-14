pipeline {
    agent any

    tools {
        nodejs 'nodejs-22-lts'
    }

    // GitHub cannot deliver webhooks to a Jenkins controller bound to localhost.
    // Poll the private repository every two minutes and build only when it changes.
    triggers {
        pollSCM('H/2 * * * *')
    }

    options {
        timestamps()
        timeout(time: 60, unit: 'MINUTES')
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '20'))
    }

    parameters {
        choice(
            name: 'DEPLOY_ENV',
            choices: ['staging', 'none'],
            description: 'Environment to deploy after a successful CI build.'
        )
        booleanParam(
            name: 'SKIP_DEPLOY',
            defaultValue: true,
            description: 'Keep enabled until the first staging deployment is verified.'
        )
        booleanParam(
            name: 'FORCE_DEPLOYMENT_FAILURE',
            defaultValue: false,
            description: 'Audit only: stop the new frontend to verify automatic rollback.'
        )
        booleanParam(
            name: 'FORCE_BUILD_FAILURE',
            defaultValue: false,
            description: 'Audit only: fail early to verify failure handling and email notification.'
        )
        booleanParam(
            name: 'FORCE_TEST_FAILURE',
            defaultValue: false,
            description: 'Audit only: fail a JUnit test to verify report publication and deployment blocking.'
        )
    }

    stages {
        stage('Controlled Failure Test') {
            when {
                expression { params.FORCE_BUILD_FAILURE }
            }

            steps {
                error('Intentional audit failure requested through FORCE_BUILD_FAILURE.')
            }
        }

        stage('Build and Test') {
            parallel {
                stage('Backend') {
                    steps {
                        sh '''
                            set -eu

                            for service in \
                                api-gateway \
                                discovery-service \
                                user-service \
                                product-service \
                                media-service
                            do
                                echo "Building and testing ${service}"
                                (
                                    cd product-service
                                    sh ./mvnw -B -ntp \
                                        -f "../${service}/pom.xml" \
                                        clean verify
                                )
                            done
                        '''
                    }
                }

                stage('Frontend') {
                    steps {
                        dir('frontend') {
                            sh 'npm ci'
                            sh 'npm test -- --watch=false'
                            sh 'npm run build'
                        }
                    }
                }
            }
        }

        stage('Archive Artifacts') {
            steps {
                archiveArtifacts(
                    artifacts: '**/target/*.jar, frontend/dist/**',
                    fingerprint: true
                )
            }
        }

        stage('Deploy to Staging') {
            when {
                expression {
                    params.DEPLOY_ENV == 'staging' && !params.SKIP_DEPLOY
                }
            }

            environment {
                JWT_SECRET = credentials('buy01-jwt-secret')
                FORCE_DEPLOYMENT_FAILURE = "${params.FORCE_DEPLOYMENT_FAILURE}"
            }

            steps {
                sh 'sh scripts/deploy-staging.sh "$GIT_COMMIT"'
            }
        }
    }

    post {
        always {
            junit(
                testResults: '**/target/surefire-reports/*.xml',
                allowEmptyResults: true
            )
        }

        success {
            echo 'Application build and tests completed successfully.'
            emailext(
                subject: "${params.DEPLOY_ENV == 'staging' && !params.SKIP_DEPLOY ? 'Deployment' : 'Build'} SUCCESS: ${env.JOB_NAME} #${env.BUILD_NUMBER}",
                body: """Job: ${env.JOB_NAME}
Build: #${env.BUILD_NUMBER}
Result: SUCCESS
Branch: ${env.BRANCH_NAME ?: 'main'}
Commit: ${env.GIT_COMMIT ?: 'unknown'}
Environment: ${params.DEPLOY_ENV}
Deployment skipped: ${params.SKIP_DEPLOY}
Duration: ${currentBuild.durationString}
Details: ${env.BUILD_URL}
""",
                to: '$DEFAULT_RECIPIENTS'
            )
        }

        failure {
            echo 'Build, tests, or deployment failed. Review the stage logs and rollback result.'
            emailext(
                subject: "Pipeline FAILURE: ${env.JOB_NAME} #${env.BUILD_NUMBER}",
                body: """Job: ${env.JOB_NAME}
Build: #${env.BUILD_NUMBER}
Result: FAILURE
Branch: ${env.BRANCH_NAME ?: 'main'}
Commit: ${env.GIT_COMMIT ?: 'unknown'}
Environment: ${params.DEPLOY_ENV}
Deployment skipped: ${params.SKIP_DEPLOY}
Controlled build failure: ${params.FORCE_BUILD_FAILURE}
Controlled test failure: ${params.FORCE_TEST_FAILURE}
Rollback: review the Deploy to Staging console output when deployment was attempted.
Duration: ${currentBuild.durationString}
Details: ${env.BUILD_URL}
""",
                attachLog: true,
                compressLog: true,
                to: '$DEFAULT_RECIPIENTS'
            )
        }

        unstable {
            emailext(
                subject: "Pipeline UNSTABLE: ${env.JOB_NAME} #${env.BUILD_NUMBER}",
                body: """Job: ${env.JOB_NAME}
Build: #${env.BUILD_NUMBER}
Result: UNSTABLE
Commit: ${env.GIT_COMMIT ?: 'unknown'}
Environment: ${params.DEPLOY_ENV}
Duration: ${currentBuild.durationString}
Details: ${env.BUILD_URL}
""",
                to: '$DEFAULT_RECIPIENTS'
            )
        }

        aborted {
            emailext(
                subject: "Pipeline ABORTED: ${env.JOB_NAME} #${env.BUILD_NUMBER}",
                body: """Job: ${env.JOB_NAME}
Build: #${env.BUILD_NUMBER}
Result: ABORTED
Commit: ${env.GIT_COMMIT ?: 'unknown'}
Environment: ${params.DEPLOY_ENV}
Duration: ${currentBuild.durationString}
Details: ${env.BUILD_URL}
""",
                to: '$DEFAULT_RECIPIENTS'
            )
        }
    }
}
