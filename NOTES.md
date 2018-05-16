### General

- Reduce Qora2 down to core blockchain node with RESTful API access. 
Other libraries can process name-storage data into websites, or provide web-based wallet UI, etc.

- Trying to reduce number of external dependencies where possible, e.g. avoiding heavy-weight ORM like Hive, Hibernate, etc.

- Trying to reduce duplicated code, especially across transactions with lots of serialisation and signature generation.

- Transaction signatures should really be generated after creating the transaction,
compared to the old style of generating signature first (and throw-away transaction in the process) 
then using signature when creating actual transaction!

- Trying to keep most of the source structure, naming, code paths, etc. similar to old Qora to reduce brain load!

- More comments/javadoc

- More JUnit tests

### Differences due to switching from MapDB to HSQLDB

- We might need to maintain more mappings in the database, e.g. Qora address to/from public key,
as previously public key objects could be stored directly in MapDB.

- The new database tried to store the data in "rawest" form, i.e. raw ```byte[]``` signatures, not base58-encoded.

- The ```Transactions``` table contains ```creator``` column, which duplicates various child table columns, 
e.g. ```PaymentTransactions.sender```, 
so that all transactions by a specific Qora account can be quickly found without scanning all child tables.

- Trying to keep all SQL inside respective Java classes, 
e.g. operations on ```Blocks``` table  only done within ```Block.java```.

- Some MapDB-based objects had Java Map<> obejcts as their values. These will need to be unpicked into separate tables.

### Possible gen2 refactoring already

- We might need to change ```Blocks.generator``` from Qora address in VARCHAR to public key in VARBINARY,
then derive address from public key as needed.

- Ditto ```Transactions.creator``` and equivalent columns in child tables.

- Extracting values from a ```ResultSet``` by column index is efficient but prone to mistakes 
as the indexes have to be maintained by a human. There might be a better, general solution to this 
without having to resort to importing an external ORM library. Maybe simply ```value = resultSet.getInt(columnIndex++)```
