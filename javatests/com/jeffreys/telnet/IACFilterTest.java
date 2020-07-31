package com.jeffreys.telnet;

import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.primitives.Bytes;
import java.io.ByteArrayOutputStream;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class IACFilterTest {

  private final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
  private final IACFilter iacFilter =
      new IACFilter((bytes, length) -> byteArrayOutputStream.write(bytes, 0, length));

  @Test
  public void passThrough_noIAC() {
    String str = "testing 1234";
    iacFilter.accept(str.getBytes(UTF_8), str.length());

    assertThat(new String(byteArrayOutputStream.toByteArray(), UTF_8)).isEqualTo(str);
  }

  @Test
  public void passThrough_noIAC_doubleAccept() {
    String str = "testing 1234";
    iacFilter.accept(str.getBytes(UTF_8), str.length());
    iacFilter.accept(str.getBytes(UTF_8), str.length());

    assertThat(new String(byteArrayOutputStream.toByteArray(), UTF_8)).isEqualTo(str + str);
  }

  @Test
  public void basicTelnet_stripping() {
    byte[] iac = {
      (byte) 0xFF, (byte) 0xFB, 0x01,
      (byte) 0xFF, (byte) 0xFC, 0x01,
      (byte) 0xFF, (byte) 0xFD, 0x01,
      (byte) 0xFF, (byte) 0xFE, 0x01,
    };
    byte[] b =
        Bytes.concat(
            "This is a test".getBytes(UTF_8), iac, " of the emergency system".getBytes(UTF_8), iac);

    iacFilter.accept(b, b.length);

    assertThat(new String(byteArrayOutputStream.toByteArray(), UTF_8))
        .isEqualTo("This is a test of the emergency system");
  }

  @Test
  public void basicTelnet_optionsStripping() {
    byte[] iac = {
      (byte) 0xFF,
      (byte) 0xFB,
      0x01,
      (byte) 0xFF,
      (byte) 0xFA,
      (byte) 0x1F,
      0x00,
      0x50,
      0x00,
      0x18,
      (byte) 0xFF,
      (byte) 0xF0
    };
    byte[] b =
        Bytes.concat(
            "This is a test".getBytes(UTF_8), iac, " of the emergency system".getBytes(UTF_8), iac);

    iacFilter.accept(b, b.length);

    assertThat(new String(byteArrayOutputStream.toByteArray(), UTF_8))
        .isEqualTo("This is a test of the emergency system");
  }

  @Test
  public void basicTelnet_stripping_over_packets() {
    byte[] start_iac = {(byte) 0xFF};
    byte[] end_iac = {(byte) 0xFB, 0x01};

    byte[] b = Bytes.concat("This is a test".getBytes(UTF_8), start_iac);
    iacFilter.accept(b, b.length);

    b = Bytes.concat(end_iac, " of the emergency system".getBytes(UTF_8));
    iacFilter.accept(b, b.length);

    assertThat(new String(byteArrayOutputStream.toByteArray(), UTF_8))
        .isEqualTo("This is a test of the emergency system");
  }
}
