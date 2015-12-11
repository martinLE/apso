import sbt._
import sbt.Keys._
import com.typesafe.sbt.SbtScalariform
import com.typesafe.sbt.SbtScalariform.ScalariformKeys
import scalariform.formatter.preferences._

object ProjectBuild extends Build {

  lazy val root = Project("root", file("."))
    .aggregate(apso, apsoTestkit)
    .settings(commonSettings: _*)
    .settings(noPublishing: _*)

  lazy val apso = Project("apso", file("apso"))
    .dependsOn(apsoTestkit % "test")
    .settings(commonSettings: _*)
    .settings(publishSettings: _*)
    .settings(apsoSettings: _*)
    .settings(libraryDependencies ++= Seq(
      "com.amazonaws"                  % "aws-java-sdk-ec2"   % "1.10.38"        % "provided",
      "com.amazonaws"                  % "aws-java-sdk-s3"    % "1.10.38"        % "provided",
      "com.github.nscala-time"        %% "nscala-time"        % "2.6.0"          % "provided",
      "com.j256.simplejmx"             % "simplejmx"          % "1.12",
      "com.typesafe"                   % "config"             % "1.3.0"          % "provided",
      "com.typesafe.akka"             %% "akka-actor"         % "2.4.1"          % "provided",
      "com.twmacinta"                  % "fast-md5"           % "2.7.1",
      "com.hierynomus"                 % "sshj"               % "0.15.0"         % "provided",
      "org.bouncycastle"               % "bcprov-jdk15on"     % "1.53"           % "provided",
      "org.bouncycastle"               % "bcpkix-jdk15on"     % "1.53"           % "provided",
      "com.jcraft"                     % "jzlib"              % "1.1.3"          % "provided",
      "io.github.andrebeat"           %% "scala-pool"         % "0.1.0"          % "provided",
      "io.spray"                      %% "spray-json"         % "1.3.2"          % "provided",
      "io.spray"                      %% "spray-httpx"        % "1.3.3"          % "provided",
      "net.databinder.dispatch"       %% "dispatch-core"      % "0.11.3",
      "org.scalaz"                    %% "scalaz-core"        % "7.1.5"          % "provided",
      "org.slf4j"                      % "slf4j-api"          % "1.7.13",
      "org.specs2"                    %% "specs2-core"        % "3.6.6"          % "test",
      "org.specs2"                    %% "specs2-scalacheck"  % "3.6.6"          % "test",
      "org.specs2"                    %% "specs2-junit"       % "3.6.6"          % "test",
      "junit"                          % "junit"              % "4.12"           % "test",
      "org.scalacheck"                %% "scalacheck"         % "1.12.5"         % "test"))

  lazy val apsoTestkit = Project("apso-testkit", file("apso-testkit"))
    .settings(commonSettings: _*)
    .settings(publishSettings: _*)
    .settings(apsoTestkitSettings: _*)
    .settings(libraryDependencies ++= Seq(
      "com.typesafe.akka"             %% "akka-testkit"       % "2.4.1"          % "provided",
      "org.slf4j"                      % "slf4j-api"          % "1.7.13",
      "org.specs2"                    %% "specs2-core"        % "3.6.6"          % "provided",
      "org.specs2"                    %% "specs2-junit"       % "3.6.6"          % "provided"
  ))

  lazy val commonSettings = Defaults.coreDefaultSettings ++ formatSettings ++ Seq(
    organization := "eu.shiftforward",
    version := "0.7-SNAPSHOT",
    scalaVersion := "2.11.7",

    resolvers ++= Seq(
      "SF Nexus Releases"             at "http://NEXUS_URL/content/repositories/releases",
      "SF Nexus Snapshots"            at "http://NEXUS_URL/content/repositories/snapshots",
      "3rd Party"                     at "http://NEXUS_URL/content/repositories/thirdparty",
      "3rd Party Snapshots"           at "http://NEXUS_URL/content/repositories/thirdparty-snapshots",
      "Sonatype Repository"           at "http://oss.sonatype.org/content/repositories/releases",
      "Sonatype Snapshots Repository" at "http://oss.sonatype.org/content/repositories/snapshots",
      "Spray Repository"              at "http://repo.spray.io/",
      "Bintray Scalaz Releases"       at "http://dl.bintray.com/scalaz/releases",
      "Typesafe Repository"           at "http://repo.typesafe.com/typesafe/releases/",
      "JAnalyse Repository"           at "http://www.janalyse.fr/repository/"),

    testOptions in Test += Tests.Argument(TestFrameworks.Specs2, "junitxml", "console"),

    scalacOptions ++= Seq(
      "-deprecation",
      "-unchecked",
      "-feature",
      "-language:existentials",
      "-language:higherKinds",
      "-language:implicitConversions",
      "-language:reflectiveCalls"))

  lazy val apsoSettings = Nil

  lazy val apsoTestkitSettings = Nil

  lazy val formatSettings = SbtScalariform.scalariformSettings ++ Seq(
    ScalariformKeys.preferences in Compile := formattingPreferences,
    ScalariformKeys.preferences in Test := formattingPreferences)

  def formattingPreferences =
    FormattingPreferences()
      .setPreference(AlignParameters, true)
      .setPreference(DoubleIndentClassDeclaration, true)

  lazy val publishSettings = Seq(
    publishTo <<= version { (v: String) =>
      val sf = "http://NEXUS_URL/content/repositories/"
      if (v.trim.endsWith("SNAPSHOT"))
        Some("snapshots" at sf + "snapshots")
      else
        Some("releases"  at sf + "releases")
    })

  lazy val noPublishing = Seq(
    publish := (),
    publishLocal := ()
  )

  // configure prompt to show current project
  override lazy val settings = super.settings :+ {
    shellPrompt := { s => Project.extract(s).currentProject.id + " > " }
  }
}
