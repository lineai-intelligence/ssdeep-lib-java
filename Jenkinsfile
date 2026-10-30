@Library('jenkins-common') _

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

        // Private images: lineai ACR (ECR retired). Hub bases via dockerhub/* pull-through.
        DOCKER_BASE_REPO = "https://lineai.azurecr.io"
        DOCKER_MAVEN = "${DOCKER_BASE_REPO.replace('https://', '')}/dockerhub/library/maven:3.9.9-eclipse-temurin-17"

        // Get Credentials for the Dogfood Environment
        DOGFOOD_CREDS_EKS = credentials("CodeLogicDogfoodKubernetesEKS")

        SECONDS_SINCE_EPOCH = sh(script: 'date -u +%s', returnStdout: true).trim()

        // lineai-intelligence-build-feedback / lineai-intelligence-source-scan
        LINEAI_HOST = 'https://dogfood.app.lineai.net'
    }

    stages {
        // Only run the CI pipeline if it's one of these branches
        stage('Check Branch') {
            when {
                not {
                    expression { BRANCH_NAME ==~ /(integration|qa|master|feature\/.*|lineai\/.*)/ }
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

        stage('ACR Authentication') {
            // Authenticate once to lineai ACR for base pulls.
            // docker.withRegistry only holds credentials for the duration of its closure, so plain
            // `docker run` steps outside those blocks need this login to persist.
            steps {
                withCredentials([usernamePassword(
                        credentialsId: 'lineai_acr',
                        usernameVariable: 'ACR_USER',
                        passwordVariable: 'ACR_PASS')]) {
                    sh('''
                        echo "${ACR_PASS}" | docker login "${DOCKER_BASE_REPO##https://}" \
                            --username "${ACR_USER}" --password-stdin
                        echo "Authenticated to ${DOCKER_BASE_REPO}"
                    ''')
                }
            }
        }

        stage('Build Branch and Run UTs') {
            when {
                expression { BRANCH_NAME ==~ /(integration|qa|master|feature\/.*|lineai\/.*)/ }
            }
            steps {
                // lineai-intelligence-build-feedback: tee build/test output so it can be sent to Lineai in post{always}
                // Jenkins `sh` is dash; pipefail needs bash.
                sh('''#!/bin/bash
                    set -o pipefail
                    export NO_COLOR=1 TERM=dumb
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
                                    --define skipSpotbugs=false' 2>&1 | tee "${WORKSPACE}/build.log"
                ''')
            }
        }

        stage('Record Test Results') {
            when {
                expression { BRANCH_NAME ==~ /(integration|qa|master|feature\/.*|lineai\/.*)/ }
            }
            steps {
                // Publish SpotBugs analysis results
                // Using the Jenkins Warnings Next Generation Plugin to process SpotBugs report.
                recordIssues enabledForFailure: true, aggregatingResults: true, tool: spotBugs(pattern: '**/target/**/spotbugsXml.xml')

                // Publish JUnit test result report
                junit "**/surefire-reports/*TestUT.xml"

                // Publish coverage (JaCoCo XML via the Coverage plugin — LIN-469)
                recordCoverage(tools: [[parser: 'JACOCO']])

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
                                    --define skipSpotbugs=true                 \
                                > /tmp/deploy.log 2>&1; rc=$?; cat /tmp/deploy.log; \
                                if [ $rc -ne 0 ] && grep -q "409 Conflict" /tmp/deploy.log; then \
                                    echo "Version already in GitHub Packages (409): treating as published (GH forbids overwrite; Artifactory allowed it)"; exit 0; \
                                fi; exit $rc'
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
                        sh("docker pull \"${DOCKER_MAVEN}\"")
                        sh(new DockerRunBuilder()
                            .image(DOCKER_MAVEN)
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
                            --env CODELOGIC_HOST="https://dogfood.app.lineai.net"      \
                            --env MAVEN_PUBLISH_VERSION="${MAVEN_PUBLISH_VERSION}"        \
                            --env SCAN_SPACE_NAME="${SCAN_SPACE_NAME}"                    \
                            --interactive                                                 \
                            --pull always                                                 \
                            --rm                                                          \
                            --volume "${PWD}:/scan"                                       \
                            dogfood.app.lineai.net/codelogic_java:latest analyze       \
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

        stage('Lineai Source Scan') {
            // lineai-intelligence-source-scan — same org-wide credentials as build feedback
            when {
                expression { BRANCH_NAME ==~ /(integration|lineai\/.*)/ }
            }
            steps {
                catchError(buildResult: 'SUCCESS', stageResult: 'FAILURE') {
                    withCredentials([
                        string(credentialsId: 'lineai-build-feedback-agent-uuid', variable: 'AGENT_UUID'),
                        string(credentialsId: 'lineai-build-feedback-agent-password', variable: 'AGENT_PASSWORD')
                    ]) {
                        sh '''
                            docker run --pull always --rm \
                                --env LINEAI_HOST --env AGENT_UUID --env AGENT_PASSWORD \
                                --env SCAN_PATH_FILTER=java --env SCAN_PATH_DEPTH=-1 \
                                -v "${WORKSPACE}:/scan" \
                                --entrypoint /bin/bash \
                                lineaipublic.azurecr.io/lineai_source_java:latest \
                                -c 'set -euo pipefail
                                    printf "%s\n" "${AGENT_UUID}" > /sourceCape-agent-uuid
                                    printf "%s\n" "${AGENT_UUID}" > /sourceCape-agent-creds
                                    printf "%s\n" "${AGENT_PASSWORD}" >> /sourceCape-agent-creds
                                    printf "%s\n" "${AGENT_UUID}" > /jCape-agent-uuid
                                    printf "%s\n" "${AGENT_UUID}" > /jCape-agent-creds
                                    printf "%s\n" "${AGENT_PASSWORD}" >> /jCape-agent-creds
                                    exec /opt/entrypoint.sh analyze --type FILE \
                                        --application "ssdeep-lib-java" \
                                        --scan-space-name "https://github.com/lineai-intelligence/ssdeep-lib-java|OnboardingBaselineScanSpace" \
                                        --path /scan --method-filter "com.codelogic." --rescan'
                        '''
                    }
                }
            }
        }
    }

    // Post pipeline actions
    post {
        unstable {
            script {
                sendTeamsFailure()
            }
        }
        failure {
            script {
                sendTeamsFailure()
            }
        }

        // Always perform this code, even if the pipeline stages fail
        always {
            // lineai-intelligence-build-feedback — org-wide credentials: see PR comment (Jenkins example)
            // Patch existing build steps like this (do not curl consoleText / rawBuild):
            //   sh '''
            //     set -o pipefail
            //     export NO_COLOR=1 TERM=dumb
            //     <existing-build-command> 2>&1 | tee "${WORKSPACE}/build.log"
            //   '''
            script {
                env.LINEAI_BUILD_STATUS = "${currentBuild.currentResult}"
            }
            withCredentials([
                string(credentialsId: 'lineai-build-feedback-agent-uuid', variable: 'AGENT_UUID'),
                string(credentialsId: 'lineai-build-feedback-agent-password', variable: 'AGENT_PASSWORD')
            ]) {
                sh '''
                    if [ ! -f "${WORKSPACE}/build.log" ]; then
                        echo "No teed build.log found; build may have failed before log capture." > "${WORKSPACE}/build.log"
                    fi
                    # Pass BRANCH_NAME/GIT_COMMIT into the container — without them send_build_info
                    # falls back to `git rev-parse --abbrev-ref HEAD` and records branch "HEAD",
                    # which breaks Lineai PR correlation for improvement rounds.
                    docker run --pull always --rm \
                        -e BRANCH_NAME="${BRANCH_NAME}" \
                        -e GIT_COMMIT="${GIT_COMMIT}" \
                        -v "${WORKSPACE}:/scan" \
                        -v "${WORKSPACE}:/log_file_path" \
                        lineaipublic.azurecr.io/lineai_send-build-info:latest \
                        send_build_info \
                        --server="${LINEAI_HOST}" \
                        --agent-uuid="${AGENT_UUID}" \
                        --agent-password="${AGENT_PASSWORD}" \
                        --log-file="/log_file_path/build.log" \
                        --pipeline-system="Jenkins" \
                        --job-name="${JOB_NAME}" \
                        --build-number="${BUILD_NUMBER}" \
                        --build-status="${LINEAI_BUILD_STATUS}"
                '''
            }

            // Drop the ACR credentials written by the ACR Authentication stage
            sh('docker logout "${DOCKER_BASE_REPO##https://}" || true')

            // Clean out the workspace
            cleanWs()
        }
    }
}
