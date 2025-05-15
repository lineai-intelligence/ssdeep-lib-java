#!/usr/bin/env bash
# https://stackoverflow.com/questions/4774054/reliable-way-for-a-bash-script-to-get-the-full-path-to-itself
# Reliable way to know where we are within the bash script

#set -v
#set -x

if [[ -z ${INCLUDES+x} ]]; then
    pushd `dirname $0` > /dev/null
    DIR=`pwd`
    popd > /dev/null
    INCLUDES="$DIR"
fi

# Run the build
docker run --rm                        \
  -i -t                                \
  --workdir /tmp/app/                  \
  --volume /tmp:/tmp                   \
  --volume ~/.m2/:/tmp/.m2/            \
  --user `id -u`:`id -g`               \
  --volume "$INCLUDES/../":/tmp/app/     \
  -e MAVEN_CONFIG=/tmp/.m2             \
  maven:3.9.9-amazoncorretto-11                    \
  sh -c 'mvn clean install -Dmaven.repo.local=/tmp/.m2/repository/ -Duser.home=/tmp'

