name := "group2-overlaynetworksimulator"

version := "0.1"

scalaVersion := "2.12.12"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor-typed" % "2.6.10",
  "com.typesafe.akka" %% "akka-actor-testkit-typed" % "2.6.10" % Test,
  "ch.qos.logback" % "logback-classic" % "1.2.3",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.9.2",
  "com.typesafe" % "config" % "1.4.0",
  "org.cloudsimplus" % "cloudsim-plus" % "5.4.3",
  "com.novocode" % "junit-interface" % "0.11" % "test",
  "junit" % "junit" % "4.12" % "test",
  "org.scalaj" %% "scalaj-http" % "2.4.2",
  "com.typesafe.akka" %% "akka-stream" % "2.6.10",
  "com.typesafe.akka" %% "akka-stream-typed" % "2.6.10",
  "com.typesafe.akka" %% "akka-http" % "10.2.0",
  "commons-lang" % "commons-lang" % "2.6",
  "commons-io" % "commons-io" % "2.6",
  //"com.typesafe.akka" %% "akka-http-spray-json" % "10.2.0",
  "com.typesafe.akka" %% "akka-serialization-jackson" % "2.6.10",
  "com.google.code.gson" % "gson" % "2.3.1",
  "com.typesafe.akka" %% "akka-cluster-typed"         % "2.6.10",
  "com.typesafe.akka" %% "akka-cluster-sharding-typed" %"2.6.10"

)