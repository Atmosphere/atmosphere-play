import sbt._
import Keys._
import PlayProject._

object ApplicationBuild extends Build {

    def fromEnv(name: String) = System.getenv(name) match {
        case null => None
        case value => Some(value)
    }

    val appName         = fromEnv("project.artifactId").getOrElse("atmosphere-play-chat")
    val appVersion      = fromEnv("project.version").getOrElse("1.0.0-SNAPSHOT")

    val appDependencies = Seq(
        // Dependencies are managed by maven
        // "org.atmosphere" % "atmosphere-runtime" % "1.1.0.SNAPSHOT",
        // "org.atmosphere" % "atmosphere-play" % "1.0.0-SNAPSHOT"
    )

    val main = PlayProject(appName, appVersion, appDependencies, mainLang = JAVA).settings()

}
