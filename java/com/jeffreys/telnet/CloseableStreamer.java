package com.jeffreys.telnet;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

interface CloseableStreamer extends Closeable {
  InputStream getInputStream() throws IOException;

  OutputStream getOutputStream() throws IOException;
}
