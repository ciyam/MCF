#!/usr/bin/env bash

set -e

git pull --rebase
mvn clean
mvn package
cp target/MCF-core*.jar MCF-core.jar

git add MCF-core.jar

git commit -m 'new JAR'
git push
