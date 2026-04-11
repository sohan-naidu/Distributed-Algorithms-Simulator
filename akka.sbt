val AKKA_TOKEN = sys.env.getOrElse("AKKA_TOKEN", "")
ThisBuild / resolvers += "akka-secure-mvn" at s"https://repo.akka.io/${AKKA_TOKEN}/secure"
ThisBuild / resolvers += Resolver.url("akka-secure-ivy", url(s"https://repo.akka.io/${AKKA_TOKEN}/secure"))(Resolver.ivyStylePatterns)