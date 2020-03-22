package com.jeffreys.telnet;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

final class SocketCloseableStreamer implements CloseableStreamer {
  private final Socket socket;

  SocketCloseableStreamer(Socket socket) {
    this.socket = checkNotNull(socket);
  }

  @Override
  public void close() throws IOException {
    socket.close();
  }

  @Override
  public InputStream getInputStream() throws IOException {
    return socket.getInputStream();
  }

  @Override
  public OutputStream getOutputStream() throws IOException {
    return socket.getOutputStream();
  }
}
