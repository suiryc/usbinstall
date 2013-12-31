package usbinstall.util

import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import ch.qos.logback.core.encoder.EchoEncoder
import java.io.{ByteArrayOutputStream, IOException}
import org.slf4j.LoggerFactory


class ProxyAppender extends AppenderBase[ILoggingEvent] {

  override def start() {
    super.start()
  }

  override def append(event: ILoggingEvent) {
    println(event.getMessage())
  }

}
