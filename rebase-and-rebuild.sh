#!/usr/bin/env bash

set -e

commit_msg="Rebased, auto-update JAR"

echo 'Checking for previous JAR commit to remove'
top_commit=$(git log -n 1 --format=%s)
if [ "${top_commit}" = "${commit_msg}" ]; then
	echo 'Removing previous JAR commit'
	git reset --hard HEAD^
	git push --force-with-lease
fi

echo 'Rebasing using master branch'
git fetch -p origin
git rebase origin/master

echo 'Pushing rebased branch'
git push --force-with-lease

echo 'Building new JAR'
mvn clean
mvn package
cp target/MCF-core*.jar MCF-core.jar

echo 'Pushing new JAR commit'
git add MCF-core.jar
git commit -m "${commit_msg}"
git push
