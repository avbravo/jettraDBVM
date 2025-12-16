# Count Command

The `count` command allows you to count the number of documents in a specific collection.

## Usage

```bash
count <collection_name>
```

## Example

```bash
jettra> use mydb
Switched to db mydb
jettra:mydb> count users
Count: 42
```

## Description

The command efficiently returns the total count of documents in the collection without retrieving them.
