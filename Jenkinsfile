@Library('jenkins-common@1.7.0') _

import net.lineai.jenkins.common.DockerRunBuilder

pipeline {
    // Run only on agent where Docker is installed
    agent { node { label 'jenkins-linux-autostart-build-agent' } }

    options {
        // Discard everything except the last 10 builds
        buildDiscarder(logRotator(numToKeepStr: '10'))

        // This fixes the issue where builds are prevented due to "Suppress automatic SCM triggering" being enabled by Jenkins
        overrideIndexTriggers(true)

        timeout(time: 1, unit: 'HOURS')
        timestamps()
    }

    environment {
        // Get Jenkins Artifactory Credentials
        GITHUB_CREDS = credentials('jenkins-github-pat-password')

        // Use docker images from our AWS ECR
        DOCKER_BASE_REPO = "https://130246223486.dkr.ecr.us-east-2.amazonaws.com"
        DOCKER_MAVEN = "${DOCKER_BASE_REPO.replace('https://', '')}/maven:3.6.3-jdk-11"
        DOCKER_MAVEN_3_8_5 = "${DOCKER_BASE_REPO.replace('https://', '')}/maven:3.8.5-openjdk-17-slim"

        // Get Credentials for the Dogfood Environment
        DOGFOOD_CREDS_EKS = credentials("CodeLogicDogfoodKubernetesEKS")

        ECR_CREDENTIALS_ID = 'ecr:us-east-2:jenkins-cicd-aws-keys'

        SECONDS_SINCE_EPOCH = sh(script: 'date -u +%s', returnStdout: true).trim()
    }

    stages {
        // Only run the CI pipeline if it's one of these branches
        stage('Check Branch') {
            when {
                not {
                    expression { BRANCH_NAME ==~ /(integration|qa|master|feature\/.*)/ }
                }
            }
            steps {
                sh('exit 1')
            }
        }

        stage("Resolve Version") {
           steps {
               script {
                 resolveVersionData()
             }
           }
        }

        stage('ECR Authentication') {
            // NOTE: All Docker images pulled for this repo are from AWS ECR or dogfood.app.codelogic.com
            // We authenticate once with ECR at the start of the pipeline (ECR tokens are valid for 12 hours).
            // If Docker Hub (docker.io) pulls are needed in the future, they should be added using
            // docker.withRegistry() blocks with Docker Hub credentials. Docker stores credentials per registry,
            // so adding docker.withRegistry() for docker.io will NOT negate our AWS ECR authentication.
            steps {
                script {
                    echo "Authenticating with AWS ECR..."

                    // Authenticate with ECR using AWS credentials from Jenkins
                    withCredentials([
                        [$class: 'AmazonWebServicesCredentialsBinding',
                         credentialsId: "${ECR_CREDENTIALS_ID.split(':').last()}",
                         accessKeyVariable: 'AWS_ACCESS_KEY_ID',
                         secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']
                    ]) {
                        sh('''
                            export AWS_REGION="us-east-2"
                            export AWS_DEFAULT_REGION="us-east-2"

                            # Strip https:// prefix from DOCKER_BASE_REPO to get registry URL
                            ECR_REGISTRY="${DOCKER_BASE_REPO##https://}"

                            # Authenticate Docker with ECR (token valid for 12 hours)
                            if aws ecr get-login-password --region us-east-2 | docker login --username AWS --password-stdin "${ECR_REGISTRY}"; then
                                echo "✓ Successfully authenticated Docker with ECR"
                            else
                                echo "✗ Failed to authenticate Docker with ECR"
                                exit 1
                            fi
                        ''')
                    }
                }
            }
        }

        stage('Build Branch and Run UTs') {
            when {
                expression { BRANCH_NAME ==~ /(integration|qa|master|feature\/.*)/ }
            }
            steps {
                sh('''
                    docker pull "${DOCKER_MAVEN}"
                    docker run                                                 \
                        --env GITHUB_CREDS_PSW="${GITHUB_CREDS_PSW}" \
                        --env GITHUB_CREDS_USR="${GITHUB_CREDS_USR}" \
                        --memory="8g"                                          \
                        --rm                                                   \
                        --user "$(id -u):$(id -g)"                             \
                        --volume "${PWD}:/app/"                                \
                        --workdir /app/                                        \
                        "${DOCKER_MAVEN}"                                      \
                            sh -c 'mvn                                         \
                                --settings settings-override.xml               \
                                clean validate install                         \
                                    --define format=xml                        \
                                    --define outputDirectory=target            \
                                    --define scanpath=target                   \
                                    --define skipDependencyCheck=false         \
                                    --define skipSpotbugs=false'
                ''')
            }
        }

        stage('Record Test Results') {
            when {
                expression { BRANCH_NAME ==~ /(integration|qa|master|feature\/.*)/ }
            }
            steps {
                // Publish SpotBugs analysis results
                // Using the Jenkins Warnings Next Generation Plugin to process SpotBugs report.
                recordIssues enabledForFailure: true, aggregatingResults: true, tool: spotBugs(pattern: '**/target/**/spotbugsXml.xml')

                // Publish JUnit test result report
                junit "**/surefire-reports/*TestUT.xml"

                // Publish JaCoCo coverage reports
                step([$class: 'JacocoPublisher'])

            }
        }

        stage('Publish Artifacts') {
            when {
                expression { BRANCH_NAME ==~ /(qa|master)/ }
            }
            steps {
                sh('''
                    docker pull "${DOCKER_MAVEN}"
                    docker run                                                 \
                        --env GITHUB_CREDS_PSW="${GITHUB_CREDS_PSW}" \
                        --env GITHUB_CREDS_USR="${GITHUB_CREDS_USR}" \
                        --rm                                                   \
                        --user "$(id -u):$(id -g)"                             \
                        --volume "${PWD}:/app/"                                \
                        --workdir /app/                                        \
                        "${DOCKER_MAVEN}"                                      \
                            sh -c 'mvn                                         \
                                --settings settings-override.xml               \
                                deploy                                         \
                                    --define skipDependencyCheck=true          \
                                    --define skipSpotbugs=true'
                ''')
            }
        }

        stage('Merge to QA') {
            // Only merge Integration into QA if we're in the integration branch and all Unit Tests have passed...
            when {
                branch 'integration'
            }
            steps {
                mergeBranch("integration", "qa")
            }
        }

        stage('Merge QA to Master') {
            // Only merge QA into Master if we're in the QA branch and all Unit and Integration Tests have passed...
            when {
                branch 'qa'
            }
            steps {
                mergeBranch("qa", "master")
            }
        }

        stage('CodeLogic Scan') {
            when {
                expression { BRANCH_NAME ==~ /(integration|v.*|feature\/.*)/ }
            }
            steps {
                catchError(buildResult: 'SUCCESS', stageResult: 'FAILURE') {
                    script {
                        // Remove the transient .m2 directory
                        sh("docker pull \"${DOCKER_MAVEN_3_8_5}\"")
                        sh(new DockerRunBuilder()
                            .image(DOCKER_MAVEN_3_8_5)
                            .setShellCommand('rm -fr /app/?/.m2/ || true && rm -fr /app/.m2')
                            .setZeroUser()
                            .volume('${PWD}/', "/app/")
                            .workdir("/app/")
                            .buildCommand())
                    }
                    // Publish CodeLogic Scan to Dogfood
                    sh('''
                        docker run                                                        \
                            --env AGENT_PASSWORD="${DOGFOOD_CREDS_EKS_PSW}"               \
                            --env AGENT_UUID="${DOGFOOD_CREDS_EKS_USR}"                   \
                            --env CODELOGIC_HOST="https://dogfood.app.codelogic.com"      \
                            --env MAVEN_PUBLISH_VERSION="${MAVEN_PUBLISH_VERSION}"        \
                            --env SCAN_SPACE_NAME="${SCAN_SPACE_NAME}"                    \
                            --interactive                                                 \
                            --pull always                                                 \
                            --rm                                                          \
                            --volume "${PWD}:/scan"                                       \
                            dogfood.app.codelogic.com/codelogic_java:latest analyze       \
                                --application "ssdeep-lib-java-${MAVEN_PUBLISH_VERSION}"  \
                                --expunge-scan-sessions                                   \
                                --method-filter com.codelogic.                            \
                                --path /scan                                              \
                                --recursive ${U+002A}                                     \
                                --scan-space-name \\"${SCAN_SPACE_NAME}\\"
                    ''')
                }
            }
        }
    }

    // Post pipeline actions
    post {
        unstable {
            script {
                sendSlackFailure()
            }
        }
        failure {
            script {
                sendSlackFailure()
            }
        }

        // Always perform this code, even if the pipeline stages fail
        always {
            // Clean out the workspace
            cleanWs()
        }
    }
}
