package usbinstall.util

import ch.qos.logback.classic.{Level, LoggerContext}
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import ch.qos.logback.core.encoder.EchoEncoder
import java.io.{ByteArrayOutputStream, IOException}
import org.slf4j.LoggerFactory
import suiryc.scala.misc.{MessageLevel, MessageWriter}

/** XXX - do we want/can access the associated Throwable if any ? */
class ProxyAppender(writers: Seq[MessageWriter])
  extends AppenderBase[ILoggingEvent]
{

  override def start() {
    super.start()
  }

  override def append(event: ILoggingEvent) {
    val msg = event.getMessage
    val level = event.getLevel.levelInt match {
      case Level.TRACE_INT => MessageLevel.TRACE
      case Level.DEBUG_INT => MessageLevel.DEBUG
      case Level.INFO_INT => MessageLevel.INFO
      case Level.WARN_INT => MessageLevel.WARNING
      case Level.ERROR_INT => MessageLevel.ERROR
    }

    writers foreach { writer =>
      writer.write(level, msg)
    }
  }

}
