addSbtPlugin("org.scoverage" % "sbt-scoverage" % "2.4.4")
addSbtPlugin("nl.gn0s1s" % "sbt-dotenv" % "3.2.0")

val AKKA_TOKEN = sys.env.getOrElse("AKKA_TOKEN", "")

resolvers += "akka-secure-mvn" at s"https://repo.akka.io/${AKKA_TOKEN}/secure"
resolvers += Resolver.url("akka-secure-ivy", url(s"https://repo.akka.io/${AKKA_TOKEN}/secure"))(Resolver.ivyStylePatterns)

addSbtPlugin("com.lightbend.cinnamon" % "sbt-cinnamon" % "2.22.2")