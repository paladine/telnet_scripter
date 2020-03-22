package com.jeffreys.telnet;

import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.function.Consumer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ScriptParserTest {
  private final Consumer<String> mockLauncher = (Consumer<String>) mock(Consumer.class);
  private final ScriptParser parser = new ScriptParser(mockLauncher);

  @Test
  public void emptyString_noLaunch() {
    String str = "";
    parser.accept(str.getBytes(), str.length());

    verify(mockLauncher, never()).accept(anyString());
  }

  @Test
  public void matchesString_launch() {
    String str = "#!script test.sh\r\nYep";
    parser.accept(str.getBytes(), str.length());

    verify(mockLauncher).accept("test.sh");
  }

  @Test
  public void matchesString_extraSpace_launch() {
    String str = "#!script    test.sh   \r\nYep";
    parser.accept(str.getBytes(), str.length());

    verify(mockLauncher).accept("test.sh");
  }

  @Test
  public void matchesString_inTheMiddle_launch() {
    String str = "Hello this is a sample\r\nOf Things#!script test.sh\r\nYep";
    parser.accept(str.getBytes(), str.length());

    verify(mockLauncher).accept("test.sh");
  }

  @Test
  public void matchesString_retryInTheMiddle_launch() {
    String str = "Hello this is a sample\r\nOf Things#!scr#!script test.sh\r\nYep";
    parser.accept(str.getBytes(), str.length());

    verify(mockLauncher).accept("test.sh");
  }

  @Test
  public void matchesString_withBackspaces_launch() {
    String str = "Hello this is a sample\r\nOf Things#!script test.sh\b\bbat\r\nYep";
    parser.accept(str.getBytes(), str.length());

    verify(mockLauncher).accept("test.bat");
  }

  @Test
  public void multipleLaunches_launch() {
    String str = "Hello this is a sample\r\n#!script test.bat\r\n#!script test2.bat\r\n";
    parser.accept(str.getBytes(), str.length());

    verify(mockLauncher).accept("test.bat");
    verify(mockLauncher).accept("test2.bat");
  }

  @Test
  public void emptyLaunchString_noLaunch() {
    String str = "!script \r\nYo there!";
    parser.accept(str.getBytes(), str.length());

    verify(mockLauncher, never()).accept(anyString());
  }

  @Test
  public void emptyLaunchString_lotsofBackspaces_noLaunch() {
    String str = "#!script \bWhatever\b\b\b\b\b\b\b\b\b\b\r\nYo there!";
    parser.accept(str.getBytes(), str.length());

    verify(mockLauncher, never()).accept(anyString());
  }

  @Test
  public void emptyLaunchString_lotsofBackspaces_butWithSomething_launches() {
    String str = "#!script \bWhatever\b\b\r\nYo there!";
    parser.accept(str.getBytes(), str.length());

    verify(mockLauncher).accept("Whatev");
  }

  @Test
  public void launchEventuallyOverflows() {
    String str =
        "#!script This is a really long long long long long long long long long line"
            + "This is a really long long long long long long long long long line"
            + "This is a really long long long long long long long long long line"
            + "This is a really long long long long long long long long long line"
            + "This is a really long long long long long long long long long line"
            + "This is a really long long long long long long long long long line"
            + "This is a really long long long long long long long long long line\r\n";
    parser.accept(str.getBytes(), str.length());

    verify(mockLauncher, never()).accept(anyString());
  }

  @Test
  public void launchEventuallyOverflows_thenRecovers_andLaunches() {
    String str =
        "#!script This is a really long long long long long long long long long line"
            + "This is a really long long long long long long long long long line"
            + "This is a really long long long long long long long long long line"
            + "This is a really long long long long long long long long long line"
            + "This is a really long long long long long long long long long line"
            + "This is a really long long long long long long long long long line"
            + "This is a really long long long long long long long long long line\r\n"
            + "#!script test.sh\r\n";
    parser.accept(str.getBytes(), str.length());

    verify(mockLauncher).accept("test.sh");
  }
}
