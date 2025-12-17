# JettraDB Benchmark Results
Date: mar 16 dic 2025 22:03:16 EST

Running benchmarks with Java 25 Compact Object Headers optimization.

Command executed: `java -XX:+UseCompactObjectHeaders -jar jettraDBVM.jar`

```
========================================
    JettraDB Benchmark Comparison
========================================
Logging in...

>>> Benchmarking JettraBasicStore (bench_basic_db)
Inserting 1000 documents...
Insertions took 6081 ms
Total Size: 4,0M (143401 bytes)
Average Size per Doc: 143 bytes
Updating 1000 documents...
Updates took 5905 ms
Deleting 1000 documents...
Deletions took 5613 ms

>>> Benchmarking JettraEngineStore (bench_engine_db)
Inserting 1000 documents...
Insertions took 5793 ms
Total Size: 4,0M (143401 bytes)
Average Size per Doc: 143 bytes
Updating 1000 documents...
Updates took 5865 ms
Deleting 1000 documents...
Deletions took 5483 ms

========================================
Benchmark Complete
```

