package usbinstall.util

import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import ch.qos.logback.core.encoder.EchoEncoder
import java.io.{ByteArrayOutputStream, IOException}
import org.slf4j.LoggerFactory
import suiryc.scala.io.LineWriter

/* XXX - output may have method with more than one parameter to be able to
 *       distinguish trace/debug/info/warn/error message level
 */
class ProxyAppender(writers: Seq[LineWriter])
  extends AppenderBase[ILoggingEvent]
{

  override def start() {
    super.start()
  }

  override def append(event: ILoggingEvent) {
    val msg = event.getMessage

    writers foreach { writer =>
      writer.write(msg)
    }
  }

}
