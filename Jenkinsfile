@Library("jenkins-common@1.3.9") _

import com.codelogic.jenkins.common.DockerRunBuilder

pipeline {
    // Run only on agent where Docker is installed
    agent { node { label 'jenkins-linux-autostart-build-agent' } }

    options {
        // Discard everything except the last 10 builds
        buildDiscarder(logRotator(numToKeepStr: '10'))

        // This fixes the issue where builds are prevented due to "Suppress automatic SCM triggering" being enabled by Jenkins
        overrideIndexTriggers(true)

        timestamps()
        timeout(time: 1, unit: 'HOURS')
    }

    environment {
        SECONDS_SINCE_EPOCH = sh(script: 'date -u +%s', returnStdout: true).trim()

        // Get Jenkins Artifactory Credentials
        ARTIFACTORY_CREDS = credentials('JenkinsArtifactory')

        // Use docker images from our AWS ECR
        DOCKER_BASE_REPO = "https://130246223486.dkr.ecr.us-east-2.amazonaws.com"
        DOCKER_CREDENTIALS = "ecr:us-east-2:brandontylkeawscreds"
        DOCKER_MAVEN = "130246223486.dkr.ecr.us-east-2.amazonaws.com/maven:3.6.3-jdk-11"
        DOCKER_MAVEN_3_8_5 = "130246223486.dkr.ecr.us-east-2.amazonaws.com/maven:3.8.5-openjdk-17-slim"

        // Get Credentials for the Dogfood Environment
        DOGFOOD_CREDS_EKS = credentials("CodeLogicDogfoodKubernetesEKS")
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
                sh 'exit 1'
            }
        }

        stage("Resolve Version") {
           steps {
               script {
                 resolveVersionData()
             }
           }
        }

        stage('Build Branch and Run UTs') {
            when {
                expression { BRANCH_NAME ==~ /(integration|qa|master|feature\/.*)/ }
            }
            steps {
                script {
                    docker.withRegistry(DOCKER_BASE_REPO, DOCKER_CREDENTIALS) {
                        // Maven steps
                        sh('''
                            docker run                                                 \
                                --env "ARTIFACTORY_CREDS_PSW=${ARTIFACTORY_CREDS_PSW}" \
                                --env "ARTIFACTORY_CREDS_USR=${ARTIFACTORY_CREDS_USR}" \
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
                script {
                    docker.withRegistry(DOCKER_BASE_REPO, DOCKER_CREDENTIALS) {
                        // Publish Artifacts to Artifactory
                        sh('''
                            docker run                                                 \
                                --env "ARTIFACTORY_CREDS_PSW=${ARTIFACTORY_CREDS_PSW}" \
                                --env "ARTIFACTORY_CREDS_USR=${ARTIFACTORY_CREDS_USR}" \
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
                        docker.withRegistry(DOCKER_BASE_REPO, DOCKER_CREDENTIALS) {
                            // Remove the transient .m2 directory
                            sh(new DockerRunBuilder()
                                .image(DOCKER_MAVEN_3_8_5)
                                .setShellCommand('rm -fr /scan/?/.m2/ && rm -fr /app/.m2')
                                .setZeroUser()
                                .volume('${PWD}/', "/app/")
                                .workdir("/app/")
                                .buildCommand())
                        }
                    }
                    // Publish CodeLogic Scan to Dogfood
                    sh('''
                        docker run                                                        \
                            --env "AGENT_PASSWORD=${DOGFOOD_CREDS_EKS_PSW}"               \
                            --env "AGENT_UUID=${DOGFOOD_CREDS_EKS_USR}"                   \
                            --env "CODELOGIC_HOST=https://dogfood.app.codelogic.com"      \
                            --env "MAVEN_PUBLISH_VERSION=${MAVEN_PUBLISH_VERSION}"        \
                            --env "SCAN_SPACE_NAME=${SCAN_SPACE_NAME}"                    \
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
