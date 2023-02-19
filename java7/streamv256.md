# PerfMark Streaming Format

The PerfMark Streaming Format is a wire protocol designed for sending trace
data across the network, or storing to disk.  The format assumes a state 
machine for compressing data, that both the encoder and decoder keep in sync.
The format is designed for encoding speed rather than decoding speed, to allow
low overhead for the tracer.

Trace data is broken down into a set of op codes, along with arguments to 
indicate what trace events the decoder should observe.  Each operation has
a static size of data present in the argument, followed by a dynamic size of
data.  Each op code is a 2 octet integer, followed by the static
data.  The static data can then indicate how much dynamic data will follow.
Integral types are always in 2's complement little-endian format.

The core data types for the format are:
* Short - 2 Octets - unsigned
* Integer - 4 octets - signed
* Long - 4 octets - signed
* String - 2 octets (+ a dynamic portion)

## Strings

The PerfMark Streaming Format uses two String tables to avoid duplication.  
Each string is encoded as a short, representing either a UTF-8 length, or an
index into a string table.  There are at most 49,152 (`2^16 - 2^14`) entries
in a string table.  The short representing the string is partitioned into 
three ranges:

* `[0, 49152)` - Refers to a position in the string table.
* `[49152, 57344)` - Length in octets of a UTF-8 encoded string.  The size is
    equal to the value of the short, minus `49152`, giving an effective range
    of `[0, 8192)`.  The string is *added* to the table.
* `[57344, 65536)` - Length in octets of a UTF-8 encoded string.  The size is
    equal to the value of the short, minus `57344`, giving an effective range
    of `[0, 8192)`.  The string is *not added* to the table.

Each table is structured like a treadmill, with old entries being removed from
one end as new entries are added to the other.  Each new entry shifts the 
position of each other entry, increasing their offset by 1.  The size of the 
table is determined by the initial header at the beginning of the stream, and
cannot change.

Tables are governed by two sizes:
* The number of strings in the table.  
* The octet size of the strings in the table

Both sizes are enforced for each new string added.  The octet size of each 
string is defined as the UTF-8 length of the encoded string, plus 32 octets of
overhead.  When a new string is added to the table, old strings are removed 
from the end to make room.  Once there is enough room in the table for an 
additional string, each remaining entry is shifted up by 1 and the new string
is added at position 0.  If the new string is larger than the table itself, 
all strings are removed from the table and the new string is not added.

There are two tables associated with strings:

* TABLE0 - The Low Churn table.
* TABLE1 - The High Churn table.

The two tables are used based on the op code's definition.  Typically, task 
names and event names are added to TABLE0.  Tag names are typically added to
TABLE1.   This is done to avoid having dynamic data invalidating the caching
of less dynamic data.


## Operations

Each operation is a 2 byte op code, followed by the data for the
operation.  


### Header
* OP `0x4650` "PF"
* Static Size: 44 = 2 + 2 + 8 + 8 + 8 + 4 +  4 + 4 + 4
  * `0x4B4D` short - "MK" the second part of the header.
  * `0x0100` Short - The version number of the file.  The current version 
      is 256.
  * Long - Invocation ID - The low order word representing the current 
      process.  This value is opaque to PerfMark, but may be a UUID, or
      a random number. 
  * Long - Invocation ID - The high order word representing the current
    process.  This value is opaque to PerfMark, but may be a UUID, or
    a random number.
  * Long - Unix Epoch Nanos - The number of nanoseconds since PerfMark was
      initialized.  This is used as the base point for all subsequent 
      trace data timestamps.
  * Integer - TABLE0 Entry size - the max number of Strings in the table
  * Integer - TABLE0 Octet size - the max number of octets stored in the table
  * Integer - TABLE1 Entry size - the max number of Strings in the table
  * Integer - TABLE1 Octet size - the max number of octets stored in the table


### Set Generation
* OP `0x0001`
* Static Size: 8

Sets the generation number of subsequent marks.  Note that this may 
occasionally go backwards due to synchronization races in the process.