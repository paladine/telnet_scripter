package com.jeffreys.telnet;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.jeffreys.telnet.Util.close;

import com.google.common.flogger.FluentLogger;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.function.BiConsumer;

/**
 * Manages the host and remote socket connection, by bridging the data between them.
 *
 * <p>Also responsible for parsing remote script commands and launching a local script, passing
 * socket data as stdin/stdout to it.
 */
final class TelnetConnection {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private static final int READ_BUFFER_SIZE = 2048;
  private static final int PROCESS_READ_THREAD_INDEX = 2;

  private final CloseableStreamer host;
  private final CloseableStreamer remote;
  private final Thread[] threads = new Thread[3]; // 2 socket read threads + 1 process read thread
  private final ScriptParser scriptParser = new ScriptParser(this::launchScript);
  private final ProcessLauncher processLauncher;
  private final Object processLock = new Object();
  private Process process;

  TelnetConnection(
      CloseableStreamer host, CloseableStreamer remote, ProcessLauncher processLauncher) {
    this.host = checkNotNull(host);
    this.remote = checkNotNull(remote);
    this.processLauncher = checkNotNull(processLauncher);
  }

  TelnetConnection(CloseableStreamer host, CloseableStreamer remote) {
    this(host, remote, ProcessBuilder::start);
  }

  public void start() throws IOException {
    checkState(threads[0] == null && threads[1] == null);

    threads[0] =
        new Thread(
            new OutputStreamForwardingThread(
                host.getInputStream(),
                remote.getOutputStream(),
                (buffer, bytes) -> {},
                this::shutdown));
    threads[1] =
        new Thread(
            new OutputStreamForwardingThread(
                remote.getInputStream(),
                host.getOutputStream(),
                this::onRemoteDataReceived,
                this::shutdown));

    threads[0].start();
    threads[1].start();
  }

  /**
   * Called when remote data is received from the server.
   *
   * <p>It actively looks for #!script tags and also passes this data to any executing script.
   */
  private void onRemoteDataReceived(byte[] buffer, int length) {
    Process processValue;
    synchronized (processLock) {
      processValue = process;
    }

    if (processValue != null) {
      try {
        OutputStream outputStream = processValue.getOutputStream();
        outputStream.write(buffer, /* offset= */ 0, length);
        outputStream.flush();
      } catch (IOException ex) {
        logger.atWarning().log("Failed to write received data to process.");
        // if we can't write to the process, it's most likely that it has died, so pretend that it
        // has.
        onProcessDied();
      }
    }

    scriptParser.accept(buffer, length);
  }

  private void launchScript(String script) {
    synchronized (processLock) {
      if (process != null) {
        logger.atWarning().log("Process already running, cannot run two simultaneous scripts");
        return;
      }

      logger.atInfo().log("Launching script \"%s\"", script);

      ProcessBuilder processBuilder = new ProcessBuilder(script);
      processBuilder.redirectInput(ProcessBuilder.Redirect.PIPE);
      processBuilder.redirectOutput(ProcessBuilder.Redirect.PIPE);
      try {
        Process newProcess = processLauncher.start(processBuilder);
        threads[PROCESS_READ_THREAD_INDEX] =
            new Thread(
                new OutputStreamForwardingThread(
                    newProcess.getInputStream(),
                    remote.getOutputStream(),
                    (buffer, bytes) -> {},
                    this::onProcessDied));
        threads[PROCESS_READ_THREAD_INDEX].start();
        process = newProcess;
      } catch (IOException ex) {
        logger.atWarning().withCause(ex).log("Failed to launch script \"%s\"", script);
      }
    }
  }

  private void onProcessDied() {
    Process oldProcess;
    synchronized (processLock) {
      oldProcess = process;
      process = null;
    }

    if (oldProcess != null) {
      logger.atInfo().log("Closing down script process");

      close(oldProcess.getInputStream());
      close(oldProcess.getOutputStream());

      oldProcess.destroy();
    }
  }

  private void shutdown() {
    close(host);
    close(remote);
    // closing the sockets should cause the threads to exit

    onProcessDied();
  }

  /** Reads from {@link #from} and forwards to {@link #to}. */
  private class OutputStreamForwardingThread implements Runnable {
    private final InputStream from;
    private final OutputStream to;

    private final BiConsumer<byte[], Integer> onDataReceived;
    private final Runnable onClose;

    private OutputStreamForwardingThread(
        InputStream from,
        OutputStream to,
        BiConsumer<byte[], Integer> onDataReceived,
        Runnable onClose) {
      this.from = checkNotNull(from);
      this.to = checkNotNull(to);
      this.onDataReceived = checkNotNull(onDataReceived);
      this.onClose = checkNotNull(onClose);
    }

    @Override
    public void run() {
      byte[] buffer = new byte[READ_BUFFER_SIZE];
      int bytes;
      try {
        while ((bytes = from.read(buffer)) > 0) {
          to.write(buffer, /* offset= */ 0, bytes);
          to.flush();

          onDataReceived.accept(buffer, bytes);
        }
      } catch (IOException ex) {
        logger.atWarning().withCause(ex).log("Failure reading InputStream data");
      } finally {
        logger.atInfo().log("Exiting forwarding thread");
        onClose.run();
      }
    }
  }
}
