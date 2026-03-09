# Distributed-Algorithms-Simulator

```
cd netgamesim
sbt clean compile assembly
java -Xms2G -Xmx4G "-Dconfig.file=D:\Distributed-Algorithms-Simulator\ngs-config.conf" -jar target/scala-3.2.2/netmodelsim.jar graph
sfdp -x -Goverlap=scale -Tpng -o ../output/output.png D:/Distributed-Algorithms-Simulator/graph.ngs.dot
```