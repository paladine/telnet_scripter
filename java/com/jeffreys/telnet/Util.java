package com.jeffreys.telnet;

import com.google.common.flogger.FluentLogger;
import java.io.Closeable;
import java.io.IOException;
import javax.annotation.Nullable;

/** Grabbag of utility methods. */
final class Util {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  // thou shall not instantiate
  private Util() {}

  /** Safely closes {@code closeable} and ignores any exceptions thrown during the closure. */
  static void close(@Nullable Closeable closeable) {
    try {
      if (closeable != null) {
        closeable.close();
      }
    } catch (IOException ex) {
      logger.atWarning().withCause(ex).log("Failed to close Closeable");
    }
  }
}
