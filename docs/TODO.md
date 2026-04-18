
# Technical debts, limitations and future work
- Minimal test coverage. I plan to add more tests and at least have a coverage of 80%
- Generator config is not read through PureConfig. Works, but design is inconsistent with enricher and translator
- Probably add a config option for translator. Most args are read through CLI
- Possibly add flexibility to translator's injection input file
- The `framework` module is my fork of the course project repo with an updated `build.sbt` that forces Akka and Scala to a newer version. In hindsight, I should've forked the course repo from the beginning but I was too deep into development at that point. Possibly clean up this dependency.
- Although I do provide a `pipiline` command it is extremely fragile. It will only work if there are no overrides provided and all default values are used. Since graph generation is not always deterministic, enrichment generally fails because each time a new graph is generated, the edges change. So any overrides mentioned break the flow. Need to test more and possibly deprecate