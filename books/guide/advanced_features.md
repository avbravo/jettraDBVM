# Advanced Features Verification Report

This document summarizes the verification of advanced database features in JettraDBVM.

## 1. Embedded Documents

JettraDB supports storing embedded documents (nested JSON objects).

**Storage:**
- Successfully verified storing documents with nested objects (e.g., `address: { city: "New York" }`).

**Querying:**
- **Limitation:** Querying using dot notation (e.g., `FIND IN users WHERE address.city = "New York"`) is **not currently supported**.
- Result: The query returns an empty result set `[]` as it searches for a top-level field named "address.city".

## 2. Aggregations

**Syntax:**
- `AGGREGATE IN <collection> PIPELINE <json_array>`

**Status:**
- **Partially Implemented.**
- The system parses the command but currently **mocks the execution**.
- It returns the total count of documents in the collection with a message: `Pipeline processing not fully implemented`.
- Actual grouping, sorting, or filtering pipelines are not yet operational.

## 3. Joins

**Syntax:**
- SQL-style `SELECT * FROM table1 JOIN table2 ON ...`

**Status:**
- **Not Supported.**
- The SQL parser currently identifies the first table (`SELECT * FROM table1`) and executes a simple query on that table.
- The `JOIN` clause is **ignored** by the parser, resulting in a query that returns all records from the first table without any joining logic.

## 4. Count Distinct

**Status:**
- **Not Supported.**
- The `count` command supports counting all documents (`count <col>`).
- Distinct counting via `AGGREGATE` is not functional due to the aggregation limitation described above.
