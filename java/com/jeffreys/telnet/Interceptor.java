package com.jeffreys.telnet;

import static com.jeffreys.telnet.Util.close;

import com.google.common.flogger.FluentLogger;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

/** Listens for incoming connections and creates a {@link TelnetConnection} for them. */
final class Interceptor {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final Options options;

  Interceptor(Options options) {
    this.options = options;
  }

  void run() throws IOException {
    try (ServerSocket socket = new ServerSocket()) {
      socket.setReuseAddress(true);
      socket.bind(new InetSocketAddress(options.getLocalPort()));

      logger.atInfo().log("Server listening on port %d\n", options.getLocalPort());

      while (true) {
        Socket incomingSocket = null;
        Socket remoteConnection = null;
        try {
          incomingSocket = socket.accept();
          remoteConnection = new Socket(options.getRemoteHost(), options.getRemotePort());

          incomingSocket.setTcpNoDelay(true);
          remoteConnection.setTcpNoDelay(true);

          logger.atInfo().log(
              "Accepted incoming connection to remote host %s:%d",
              options.getRemoteHost(), options.getRemotePort());

          new TelnetConnection(
                  new SocketCloseableStreamer(incomingSocket),
                  new SocketCloseableStreamer(remoteConnection))
              .start();
        } catch (IOException ex) {
          logger.atWarning().withCause(ex).log(
              "Unable to accept connection/connect to remote host %s:%d",
              options.getRemoteHost(), options.getRemotePort());

          close(incomingSocket);
          close(remoteConnection);
        }
      }
    }
  }
}
