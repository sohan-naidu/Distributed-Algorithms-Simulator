# Distributed-Algorithms-Simulator

```
git clone --recursive https://github.com/sohan-naidu/Distributed-Algorithms-Simulator.git
```

### TODO: Update directories
```
cd core/generation
sbt clean compile assembly
java -Xms2G -Xmx4G "-Dconfig.file=C:\Users\sohan\Projects\Distributed-Algorithms-Simulator\ngs-config.conf" -jar target/scala-3.2.2/netmodelsim.jar graph
sfdp -x -Goverlap=scale -Tpng -o ../output/output.png C:\Users\sohan\Projects\Distributed-Algorithms-Simulator\netgamesim\graph.ngs.dot
```