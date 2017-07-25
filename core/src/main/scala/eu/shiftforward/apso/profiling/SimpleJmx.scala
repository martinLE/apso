package eu.shiftforward.apso.profiling

import java.net.ServerSocket

import com.j256.simplejmx.server.JmxServer
import eu.shiftforward.apso.Logging

import scala.util.{ Failure, Success, Try }

trait SimpleJmx extends Logging {

  def jmxConfig: config.Jmx

  private lazy val jmxHost = jmxConfig.host
  private lazy val jmxPort = jmxConfig.port

  private def startJmx(port: Option[Int] = None) = {
    def randomPort = {
      val s = new ServerSocket(0)
      val p = s.getLocalPort
      s.close()
      p
    }

    val jmx = new JmxServer(port.getOrElse(randomPort))
    jmx.start()
    jmx
  }

  // start a properly configured JMX server. When behind a firewall, both ports `jmxPort` (the RMI
  // registry port) and `jmxPort + 1` (the RMI server port) need to be open. Connections are
  // established through port `jmxPort`.
  // In the event of a binding failure to port `jmxPort`, a retry is performed with a random port.
  jmxHost.foreach { host => System.setProperty("java.rmi.server.hostname", host) }
  val jmxServer = Try(startJmx(jmxPort)).recover { case _ => startJmx() }

  jmxServer match {
    case Success(jmx) =>
      log.info("Bound JMX on port {}", jmx.getServerPort)
      sys.addShutdownHook(jmx.stop())

    case Failure(ex) =>
      log.warn("Could not start JMX server", ex)
  }
}