#!/usr/bin/env bash

################################################################################
# 
# Copyright 2025 CodeLogic, All Rights Reserved
#
# build.sh
#   Builds ssdeep-lib-java using Maven in Docker
#   Similar to Jenkinsfile (please keep them sync'd)
#
################################################################################

################################################################################
#
# FUNCTION print_usage()
#
################################################################################
print_usage() {
    cat << EOF
Usage: $0 [OPTIONS]

Builds ssdeep-lib-java using Maven in a Docker container.

OPTIONS:
    -h, --help    Display this help message and exit

ENVIRONMENT VARIABLES:
    DOCKER_REGISTRY  Docker registry to pull base images from (default: docker.io)
                     Use ECR registry (130246223486.dkr.ecr.us-east-2.amazonaws.com)
                     to pull from AWS ECR instead of Docker Hub
                     
NOTE: This script uses maven:3.9.9-amazoncorretto-11. If using ECR, ensure this
      specific version is available in ECR, or update DOCKER_MAVEN to use a
      version available in ECR (e.g., maven:3.8.5-openjdk-17-slim).

EXAMPLES:
    $0                                              # Build using Docker Hub
    DOCKER_REGISTRY=130246223486.dkr.ecr.us-east-2.amazonaws.com $0  # Build using ECR

EOF
}

################################################################################
#
# MAIN
#
################################################################################

# Check for help flag
if [[ "${1:-}" == "-h" ]] || [[ "${1:-}" == "--help" ]]; then
    print_usage
    exit 0
fi

# https://stackoverflow.com/questions/4774054/reliable-way-for-a-bash-script-to-get-the-full-path-to-itself
# Reliable way to know where we are within the bash script
if [[ -z ${INCLUDES+x} ]]; then
    pushd "$(dirname "$0")" > /dev/null || exit
    DIR="$(pwd)"
    popd > /dev/null || exit
    INCLUDES="${DIR}"
fi

# Default to Docker Hub for local builds unless explicitly set
DOCKER_REGISTRY=${DOCKER_REGISTRY:-docker.io}
DOCKER_MAVEN="${DOCKER_REGISTRY}/maven:3.9.9-amazoncorretto-11"

# Run the build
docker run --rm                        \
  -i -t                                \
  --workdir /tmp/app/                  \
  --volume /tmp:/tmp                   \
  --volume ~/.m2/:/tmp/.m2/            \
  --user "$(id -u):$(id -g)"           \
  --volume "${INCLUDES}/../:/tmp/app/"  \
  -e MAVEN_CONFIG=/tmp/.m2             \
  "${DOCKER_MAVEN}"                    \
  sh -c 'mvn clean install -Dmaven.repo.local=/tmp/.m2/repository/ -Duser.home=/tmp'

