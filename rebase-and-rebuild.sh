#!/usr/bin/env bash

set -e

commit_msg="Rebased, XORed, auto-update JAR"

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

echo 'Building new XORed auto-update JAR'
mvn clean
mvn package
java -cp target/MCF-core*.jar org.qora.XorUpdate target/MCF-core*.jar MCF-core.update

echo 'Pushing new JAR commit'
git add MCF-core.update
git commit -m "${commit_msg}"
git push
