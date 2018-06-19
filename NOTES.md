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

- SQL is contained within repository classes repository.* (interfaces) and repository.hsqldb.* (implementations).

- We use transfer objects in data.*

- "Business logic" is left in qora.* 

- Some MapDB-based objects had Java Map<> objects as their values. These will need to be unpacked into separate tables.
