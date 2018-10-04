# Qora2

To use:

- Use maven to fetch dependencies.
- Build project.
- Build v1feeder.jar as a fatjar using src/v1feeder.java as the main class
- Fire up an old-gen Qora node.
- Use ```v1feeder.jar``` to migrate old Qora blocks to DB:

```java -jar v1feeder.jar qora-v1-node-ip```

You should now be able to run all the JUnit tests.

You can also examine the migrated database using 
[HSQLDB's "sqltool"](http://www.hsqldb.org/doc/2.0/util-guide/sqltool-chapt.html).

It's a good idea to install "rlwrap" (ReadLine wrapper) too as sqltool doesn't
support command history/editing!

Typical command line for sqltool would be:
```
rlwrap java -cp ${HSQLDB_JAR}:${SQLTOOL_JAR} org.hsqldb.cmdline.SqlTool --rcFile=${SQLTOOL_RC} qora
```

```${HSQLDB_JAR}``` contains pathname to where Maven downloaded hsqldb, 
typically ```${HOME}/.m2/repository/org/hsqldb/hsqldb/2.4.0/hsqldb-2.4.0.jar```,
but for now ```lib/org/hsqldb/hsqldb/r5836/hsqldb-r5836.jar```

```${SQLTOOL_JAR}``` contains pathname to where Maven downloaded sqltool,
typically  ```${HOME}/.m2/repository/org/hsqldb/sqltool/2.4.1/sqltool-2.4.1.jar```

```${SQLTOOL_RC}``` contains pathname to a text file describing Qora2 database, 
e.g. ```${HOME}/.sqltool.rc```, with contents like:

```
urlid qora
url jdbc:hsqldb:file:db/qora
username SA
password

urlid qora-test
url jdbc:hsqldb:file:db/test
username SA
password
```

You could change the line ```url jdbc:hsqldb:file:db/test``` to use a full pathname for easier use.

Another idea is to assign a shell alias in your ```.bashrc``` like:
```
export HSQLDB_JAR=${HOME}/.m2/repository/org/hsqldb/hsqldb/2.4.0/hsqldb-2.4.0.jar
export SQLTOOL_JAR=${HOME}/.m2/repository/org/hsqldb/sqltool/2.4.1/sqltool-2.4.1.jar
alias sqltool='rlwrap java -cp ${HSQLDB_JAR}:${SQLTOOL_JAR} org.hsqldb.cmdline.SqlTool --rcFile=${SQLTOOL_RC}'
```
So you can simply type: ```sqltool qora-test```

Don't forget to use ```SHUTDOWN;``` before exiting sqltool so that database files are closed cleanly.