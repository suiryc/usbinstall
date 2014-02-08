package usbinstall.util

import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import ch.qos.logback.core.encoder.EchoEncoder
import java.io.{ByteArrayOutputStream, IOException}
import org.slf4j.LoggerFactory


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
