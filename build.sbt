name := "handlebars"

organization := "com.gilt"

scalaVersion := "2.10.2"

crossScalaVersions := Seq("2.10.2", "2.9.1", "2.9.2")

libraryDependencies ++= Seq(
  "com.google.guava" % "guava" % "12.0",
  "org.slf4j" % "slf4j-api" % "1.6.4",
  "org.slf4j" % "slf4j-simple" % "1.6.4",
  "org.scalatest" %% "scalatest" % "1.9.1" % "test"
)

resolvers ++= Seq(
  "Sonatype.org Snapshots" at "http://oss.sonatype.org/content/repositories/snapshots",
  "Sonatype.org Releases" at "http://oss.sonatype.org/service/local/staging/deploy/maven2"
)

scalacOptions ++= Seq(
  "-deprecation",
  "-unchecked"
)

publishMavenStyle := true

publishTo <<= version { (v: String) =>
  val nexus = "https://oss.sonatype.org/"
  if (v.trim.endsWith("SNAPSHOT"))
    Some("snapshots" at nexus + "content/repositories/snapshots")
  else
    Some("releases"  at nexus + "/service/local/staging/deploy/maven2")
}

// For publishing / testing locally
//publishTo := Some(Resolver.file("m2",  new File(Path.userHome.absolutePath+"/.m2/repository")))

publishArtifact in Test := false

testOptions in Test += Tests.Argument("-oD")

pomIncludeRepository := { _ => false }

licenses := Seq("BSD-style" -> url("http://www.opensource.org/licenses/bsd-license.php"))

homepage := Some(url("https://github.com/mwunsch/handlebars.scala"))

pomExtra := (
  <scm>
    <url>git@github.com:mwunsch/handlebars.scala.git</url>
    <connection>scm:git:git@github.com:mwunsch/handlebars.scala.git</connection>
  </scm>
  <developers>
    <developer>
      <id>mwunsch</id>
      <name>Mark Wunsch</name>
      <url>http://markwunsch.com/</url>
      <organization>Gilt</organization>
      <organizationUrl>http://www.gilt.com</organizationUrl>
    </developer>
  </developers>)
