package com.jeffreys.telnet;

import static com.google.common.truth.Truth.assertThat;
import static com.jeffreys.junit.Exceptions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.auto.value.AutoValue;
import com.google.common.base.Splitter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Duration;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class TelnetConnectionTest {

  @Test
  public void simplePassThrough() throws Exception {
    CountDownLatch closeLatch = new CountDownLatch(4);
    ByteArrayOutputStream remoteOutputStream = new ByteArrayOutputStream();
    ByteArrayOutputStream hostOutputStream = new ByteArrayOutputStream();

    TestCloseableStreamer remote =
        new TestCloseableStreamer(
            closeLatch,
            new ByteArrayInputStream("Welcome to the BBS!".getBytes()),
            new CloseableOutputStream(remoteOutputStream, closeLatch));
    TestCloseableStreamer host =
        new TestCloseableStreamer(
            closeLatch,
            new ByteArrayInputStream("you typed this".getBytes()),
            new CloseableOutputStream(hostOutputStream, closeLatch));
    TelnetConnection telnetConnection = new TelnetConnection(host, remote, ProcessBuilder::start);

    telnetConnection.start();

    closeLatch.await();

    assertThat(remoteOutputStream.toString()).isEqualTo("you typed this");
    assertThat(hostOutputStream.toString()).isEqualTo("Welcome to the BBS!");
  }

  @Test
  public void launchesScript() throws Exception {
    // --------------------------------------------------------------------------------------------
    // ARRANGE
    // --------------------------------------------------------------------------------------------
    Process process = mock(Process.class);
    when(process.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[0]));
    when(process.getOutputStream()).thenReturn(new ByteArrayOutputStream());
    ProcessLauncher processLauncher = mock(ProcessLauncher.class);
    when(processLauncher.start(any(ProcessBuilder.class))).thenReturn(process);

    MessageQueue<QueueMessage> remoteQueue = new MessageQueue<>();
    MessageQueue<QueueMessage> hostQueue = new MessageQueue<>();

    CountDownLatch closeLatch = new CountDownLatch(4); // 2 input + 2 output streams
    ByteArrayOutputStream remoteOutputStream = new ByteArrayOutputStream();
    ByteArrayOutputStream hostOutputStream = new ByteArrayOutputStream();

    TestCloseableStreamer remote =
        new TestCloseableStreamer(
            closeLatch,
            new BlockingLineInputStream(
                "Welcome to the BBS!\r\nWould you like to launch a script?\r\nand another\r\n",
                remoteQueue),
            new CloseableOutputStream(remoteOutputStream, closeLatch));
    TestCloseableStreamer host =
        new TestCloseableStreamer(
            closeLatch,
            new BlockingLineInputStream("you typed this\r\n#!script /tmp/test.sh\r\none more\r\n", hostQueue),
            new CloseableOutputStream(hostOutputStream, closeLatch));
    TelnetConnection telnetConnection = new TelnetConnection(host, remote, processLauncher);

    CountDownLatch scriptLatch = new CountDownLatch(2);
    telnetConnection.setOnPostHostDataReceived((buffer, bytes) -> scriptLatch.countDown());

    // --------------------------------------------------------------------------------------------
    // ACT
    // --------------------------------------------------------------------------------------------
    telnetConnection.start();

    // 2 lines from remote, release them all
    remoteQueue.post(QueueMessage.create());
    remoteQueue.post(QueueMessage.create());
    // release the lines from host
    hostQueue.post(QueueMessage.create());
    hostQueue.post(QueueMessage.create());

    // wait for the script to launch
    assertThat(scriptLatch.await(5000, TimeUnit.MILLISECONDS)).isTrue();

    // release them both to finish up
    remoteQueue.post(QueueMessage.create());
    remoteQueue.post(QueueMessage.create());
    hostQueue.post(QueueMessage.create());
    hostQueue.post(QueueMessage.create());

    // wait for the streams to be closed
    assertThat(closeLatch.await(5000, TimeUnit.MILLISECONDS)).isTrue();

    // --------------------------------------------------------------------------------------------
    // ASSERT
    // --------------------------------------------------------------------------------------------
    assertThat(remoteOutputStream.toString()).isEqualTo("you typed this\r\n#!script /tmp/test.sh\r\none more\r\n");
    assertThat(hostOutputStream.toString())
        .isEqualTo(
            "Welcome to the BBS!\r\n"
                + "Would you like to launch a script?\r\n"
                + "and another\r\n");
    verify(processLauncher)
        .start(argThat(processBuilder -> processBuilder.command().contains("/tmp/test.sh")));
  }

  @Test
  public void doubleLaunchScript_doesntLaunchTwice() throws Exception {
    // --------------------------------------------------------------------------------------------
    // ARRANGE
    // --------------------------------------------------------------------------------------------
    MessageQueue<QueueMessage> processQueue = new MessageQueue<>();
    Process process = mock(Process.class);
    when(process.getInputStream())
        .thenReturn(
            new BlockingLineInputStream("commands\r\nfrom the\r\nscript\r\n", processQueue));
    when(process.getOutputStream()).thenReturn(new ByteArrayOutputStream());
    ProcessLauncher processLauncher = mock(ProcessLauncher.class);
    when(processLauncher.start(any(ProcessBuilder.class))).thenReturn(process);

    MessageQueue<QueueMessage> remoteQueue = new MessageQueue<>();
    MessageQueue<QueueMessage> hostQueue = new MessageQueue<>();

    CountDownLatch closeLatch = new CountDownLatch(4); // 2 input + 2 output streams
    ByteArrayOutputStream remoteOutputStream = new ByteArrayOutputStream();
    ByteArrayOutputStream hostOutputStream = new ByteArrayOutputStream();

    TestCloseableStreamer remote =
        new TestCloseableStreamer(
            closeLatch,
            new BlockingLineInputStream("Welcome to the BBS!\r\n",remoteQueue),
            new CloseableOutputStream(remoteOutputStream, closeLatch));
    TestCloseableStreamer host =
        new TestCloseableStreamer(
            closeLatch,
            new BlockingLineInputStream("you typed this\r\n#!script /tmp/first.sh\r\n#!script /tmp/second.sh\r\n", hostQueue),
            new CloseableOutputStream(hostOutputStream, closeLatch));
    TelnetConnection telnetConnection = new TelnetConnection(host, remote, processLauncher);

    CountDownLatch firstScriptLatch = new CountDownLatch(2);
    CountDownLatch secondScriptLatch = new CountDownLatch(3);
    telnetConnection.setOnPostHostDataReceived((buffer, bytes) -> {
      firstScriptLatch.countDown();
      secondScriptLatch.countDown();
    });

    // --------------------------------------------------------------------------------------------
    // ACT
    // --------------------------------------------------------------------------------------------
    telnetConnection.start();

    // 1 lines from remote, release first one
    remoteQueue.post(QueueMessage.create());
    // release the lines launching the first script
    hostQueue.post(QueueMessage.create());
    hostQueue.post(QueueMessage.create());

    // wait for the script to launch
    assertThat(firstScriptLatch.await(5000, TimeUnit.MILLISECONDS)).isTrue();
    // release the next script line which attempts to start the second script
    hostQueue.post(QueueMessage.create());
    // wait for the second script to be detected and attempted to launch
    assertThat(secondScriptLatch.await(5000, TimeUnit.MILLISECONDS)).isTrue();

    // flush all the streams now
    remoteQueue.post(QueueMessage.create());
    hostQueue.post(QueueMessage.create());

    // wait for the streams to be closed
    assertThat(closeLatch.await(5000, TimeUnit.MILLISECONDS)).isTrue();

    // --------------------------------------------------------------------------------------------
    // ASSERT
    // --------------------------------------------------------------------------------------------
    assertThat(remoteOutputStream.toString()).isEqualTo("you typed this\r\n#!script /tmp/first.sh\r\n#!script /tmp/second.sh\r\n");
    assertThat(hostOutputStream.toString()).isEqualTo("Welcome to the BBS!\r\n");
    verify(processLauncher)
        .start(argThat(processBuilder -> processBuilder.command().contains("/tmp/first.sh")));
    verify(processLauncher, times(1)).start(any());
  }

  @Test
  public void script_outputIsForwarded() throws Exception {
    // --------------------------------------------------------------------------------------------
    // ARRANGE
    // --------------------------------------------------------------------------------------------
    CountDownLatch processCloseLatch = new CountDownLatch(1);
    MessageQueue<QueueMessage> processQueue = new MessageQueue<>();
    ByteArrayOutputStream processOutputStream = new ByteArrayOutputStream();
    Process process = mock(Process.class);
    when(process.getInputStream())
        .thenReturn(
            new BlockingLineInputStream("commands\r\nfrom the\r\nscript\r\n", processQueue));
    when(process.getOutputStream())
        .thenReturn(new CloseableOutputStream(processOutputStream, processCloseLatch));
    ProcessLauncher processLauncher = mock(ProcessLauncher.class);
    when(processLauncher.start(any(ProcessBuilder.class))).thenReturn(process);

    MessageQueue<QueueMessage> remoteQueue = new MessageQueue<>();
    MessageQueue<QueueMessage> hostQueue = new MessageQueue<>();

    CountDownLatch closeLatch = new CountDownLatch(4); // 2 input + 2 output streams
    ByteArrayOutputStream remoteOutputStream = new ByteArrayOutputStream();
    ByteArrayOutputStream hostOutputStream = new ByteArrayOutputStream();

    TestCloseableStreamer remote =
        new TestCloseableStreamer(
            closeLatch,
            new BlockingLineInputStream("Welcome to the BBS!\r\n", remoteQueue),
            new CloseableOutputStream(remoteOutputStream, closeLatch));
    TestCloseableStreamer host =
        new TestCloseableStreamer(
            closeLatch,
            new BlockingLineInputStream("you typed this\r\n#!script /tmp/test.sh\r\n", hostQueue),
            new CloseableOutputStream(hostOutputStream, closeLatch));
    TelnetConnection telnetConnection = new TelnetConnection(host, remote, processLauncher);

    CountDownLatch scriptLatch = new CountDownLatch(2);
    telnetConnection.setOnPostHostDataReceived((buffer, bytes) -> scriptLatch.countDown());

    CountDownLatch remoteLatch = new CountDownLatch(1);
    telnetConnection.setOnPostRemoteDataReceived((buffer, bytes) -> remoteLatch.countDown());
    // --------------------------------------------------------------------------------------------
    // ACT
    // --------------------------------------------------------------------------------------------
    telnetConnection.start();

    // release the script lines
    hostQueue.post(QueueMessage.create());
    hostQueue.post(QueueMessage.create());

    // wait for the script to launch
    assertThat(scriptLatch.await(5000, TimeUnit.MILLISECONDS)).isTrue();

    // 1 lines from remote, release them all, so that it gets piped to the script
    remoteQueue.post(QueueMessage.create());
    assertThat(remoteLatch.await(5000, TimeUnit.MILLISECONDS)).isTrue();

    // release the process lines
    processQueue.post(QueueMessage.create());
    processQueue.post(QueueMessage.create());
    processQueue.post(QueueMessage.create());
    processQueue.post(QueueMessage.create());
    // wait for process to complete and output be to closed
    assertThat(processCloseLatch.await(5000, TimeUnit.MILLISECONDS)).isTrue();

    // release them both to finish up
    remoteQueue.post(QueueMessage.create());
    hostQueue.post(QueueMessage.create());

    // wait for the streams to be closed
    assertThat(closeLatch.await(5000, TimeUnit.MILLISECONDS)).isTrue();

    // --------------------------------------------------------------------------------------------
    // ASSERT
    // --------------------------------------------------------------------------------------------
    assertThat(remoteOutputStream.toString())
        .isEqualTo("you typed this\r\n#!script /tmp/test.sh\r\ncommands\r\nfrom the\r\nscript\r\n");
    assertThat(hostOutputStream.toString()).isEqualTo("Welcome to the BBS!\r\n");
    assertThat(processOutputStream.toString()).isEqualTo("Welcome to the BBS!\r\n");
    verify(processLauncher)
        .start(argThat(processBuilder -> processBuilder.command().contains("/tmp/test.sh")));
  }

  @Test
  public void emptyMessageQueue_throwsOnGet() {
    MessageQueue<QueueMessage> messageQueue = new MessageQueue<>();

    assertThrows(NoSuchElementException.class, () -> messageQueue.get(Duration.ofMillis(100)));
  }

  /** A {@link CloseableStreamer} that count downs the {@link CountDownLatch} when closed. */
  private static final class TestCloseableStreamer implements CloseableStreamer {
    private final CountDownLatch countDownLatch;
    private final InputStream inputStream;
    private final OutputStream outputStream;

    private TestCloseableStreamer(
        CountDownLatch countDownLatch, InputStream inputStream, OutputStream outputStream) {
      this.countDownLatch = countDownLatch;
      this.inputStream = inputStream;
      this.outputStream = outputStream;
    }

    @Override
    public void close() throws IOException {
      countDownLatch.countDown();
    }

    @Override
    public InputStream getInputStream() throws IOException {
      return inputStream;
    }

    @Override
    public OutputStream getOutputStream() throws IOException {
      return outputStream;
    }
  }

  /**
   * A simple MessageQueue implementation, similar to Win32.
   *
   * <p>After writing Win32 for over a decade, I'm most familiar with this type of synchronization.
   */
  private static final class MessageQueue<T> {
    private final BlockingQueue<T> queue = new ArrayBlockingQueue<>(/* capacity= */ 16);

    public MessageQueue() {}

    public void post(T value) {
      queue.add(value);
    }

    public T get() throws InterruptedException {
      return queue.take();
    }

    public T get(Duration waitDuration) throws InterruptedException {
      T value = queue.poll(waitDuration.toMillis(), TimeUnit.MILLISECONDS);
      if (value == null) {
        throw new NoSuchElementException("Queue fetch timeout");
      }
      return value;
    }
  }

  enum QueueAction {
    RELEASE_LINE;
  }

  @AutoValue
  abstract static class QueueMessage {
    abstract QueueAction getAction();

    static QueueMessage create() {
      return new AutoValue_TelnetConnectionTest_QueueMessage(QueueAction.RELEASE_LINE);
    }
  }

  /**
   * An {@link OutputStream} wrapper that counts down the {@link CloseableOutputStream} when closed.
   */
  private static final class CloseableOutputStream extends FilterOutputStream {
    private final CountDownLatch closeLatch;

    public CloseableOutputStream(OutputStream outputStream, CountDownLatch closeLatch) {
      super(outputStream);
      this.closeLatch = closeLatch;
    }

    @Override
    public void close() throws IOException {
      super.close();

      closeLatch.countDown();
    }
  }

  /**
   * An {@link InputStream} wrapper that returns lines of text one at a time.
   *
   * <p>To release a line of text, send a message to the {@link MessageQueue} passed to the
   * constructor.
   */
  private static final class BlockingLineInputStream extends InputStream {
    private final List<String> strings;
    private final MessageQueue<QueueMessage> messageQueue;

    private int index = 0;

    BlockingLineInputStream(String string, MessageQueue<QueueMessage> messageQueue) {
      super();
      this.strings = Splitter.on("\r\n").omitEmptyStrings().splitToList(string);
      this.messageQueue = messageQueue;
    }

    @Override
    public int read() {
      throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public int read(byte[] dest, int offset, int len) throws IOException {
      throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public int read(byte[] dest) throws IOException {
      try {
        while (true) {
          QueueMessage message = messageQueue.get();
          if (message.getAction().equals(QueueAction.RELEASE_LINE)) {
            break;
          }
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return -1;
      }

      if (index < strings.size()) {
        byte[] src = strings.get(index++).getBytes();

        int toCopy = src.length + 2;
        assertThat(dest.length).isAtLeast(toCopy);

        System.arraycopy(src, 0, dest, 0, src.length);
        dest[toCopy - 2] = '\r';
        dest[toCopy - 1] = '\n';
        return toCopy;
      } else {
        return -1;
      }
    }
  }
}
