# Qora2

To use:

- Use maven to fetch dependencies.
- Build project.
- Fire up an old-gen Qora node.
- Run ```src/migrate.java``` as a Java application to migrate old Qora blocks to DB.

You should now be able to run ```src/test/load.java``` and ```src/test/save.java```
as JUnit tests demonstrating loading/saving Transactions from/to database.

You can also examine the migrated database using 
[HSQLDB's "sqltool"](http://www.hsqldb.org/doc/2.0/util-guide/sqltool-chapt.html).

It's a good idea to install "rlwrap" (ReadLine wrapper) too as sqltool doesn't
support command history/editing!

Typical command line for sqltool would be:
```
rlwrap java -cp ${HSQLDB_JAR}:${SQLTOOL_JAR} org.hsqldb.cmdline.SqlTool --rcFile=${SQLTOOL_RC} qora
```

```${HSQLDB_JAR}``` contains pathname to ```hsqldb-2.4.0.jar```, 
typically ```${HOME}/.m2/repository/org/hsqldb/hsqldb/2.4.0/hsqldb-2.4.0.jar```

```${SQLTOOL_JAR}``` contains pathname to where you 
[downloaded sqltool-2.2.6.jar](http://search.maven.org/remotecontent?filepath=org/hsqldb/sqltool/2.2.6/sqltool-2.2.6.jar)

```${SQLTOOL_RC}``` contains pathname to a text file describing Qora2 database, 
e.g. ```${HOME}/.sqltool.rc```, with contents like:

```
urlid qora
url jdbc:hsqldb:file:db/test
username SA
password
```

You could change the line ```url jdbc:hsqldb:file:db/test``` to use a full pathname for easier use.

Another idea is to assign a shell alias in your ```.bashrc``` like:
```
alias sqltool='rlwrap java -cp ${HSQLDB_JAR}:${SQLTOOL_JAR} org.hsqldb.cmdline.SqlTool --rcFile=${SQLTOOL_RC}'
```
So you can simply type: ```sqltool qora```

Don't forget to use ```SHUTDOWN;``` before exiting sqltool so that database files are closed cleanly.