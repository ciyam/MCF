#!/usr/bin/env bash

set -e

commit_msg="Rebased, auto-update JAR"

top_commit=$(git log -n 1 --format=%s)
if [ "${top_commit}" = "${commit_msg}" ]; then
	echo 'Removing previous JAR commit'
	git reset --hard HEAD^
	git push --force
fi

git fetch -p origin
git rebase origin/master
mvn clean
mvn package
cp target/MCF-core*.jar MCF-core.jar

git add MCF-core.jar

git commit -m "${commit_msg}"
git push
