#!/usr/bin/env bash

BUNDLES="org.apache.felix.gogo.runtime org.apache.felix.gogo.shell org.apache.felix.gogo.command org.eclipse.equinox.console"
JAR=org.eclipse.osgi
PLUGIN_DIR=/usr/local/lib/eclipse/plugins

for bundle in ${BUNDLES}; do
	bundle_path=`echo ${PLUGIN_DIR}/${bundle}_*.jar`

	if [ -z "${bundles_arg}" ]; then
		bundles_arg=-Dosgi.bundles=
	else
		bundles_arg+=,
	fi

	bundles_arg+=${bundle_path}@start
done

java ${bundles_arg} -jar ${PLUGIN_DIR}/${JAR}_*.jar -console
