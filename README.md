Java Flight Recorder parser for SJK
=========

[SJK] tools offer number of reports based on thread stack sampling, inluding sophisticated filtering and [flame graph] visualization.

Java Flight Recorder dumps may include stack traces produced by method profiling. This project implements parser for Java Flight Recorder files
which can be used by [SJK].

Mission Control is a part of JDK, implementaion seeks for local Mission Control installation and add jars required for recording parsing to classpath.
If Mission Control installation is missing (e.g. in OpenJDK) Flight Recorder format support will not be available.

 [SJK]: https://github.com/aragozin/jvm-tools
 [flame graph]: http://blog.ragozin.info/2016/01/flame-graphs-vs-cold-numbers.html
