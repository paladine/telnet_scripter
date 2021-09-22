package com.jeffreys.telnet;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.ByteArrayOutputStream;
import java.util.function.BiConsumer;

public class IACFilter implements BiConsumer<byte[], Integer> {
  private static final byte WILL = (byte) 0xFB;
  private static final byte WONT = (byte) 0xFC;
  private static final byte DO = (byte) 0xFD;
  private static final byte DONT = (byte) 0xFE;
  private static final byte IAC = (byte) 0xFF;

  private static final byte SB = (byte) 0xFA;
  private static final byte SE = (byte) 0xF0;

  private enum ParseState {
    Normal,
    FoundIAC,
    IACCommand,
    SBStart,
    SBValue,
    SBIAC
  }

  // large enough for an Ethernet jumbo frame
  private final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream(10000);
  private final BiConsumer<byte[], Integer> consumer;
  private ParseState parseState = ParseState.Normal;

  public IACFilter(BiConsumer<byte[], Integer> consumer) {
    this.consumer = checkNotNull(consumer);
  }

  @Override
  public void accept(byte[] data, Integer length) {
    for (int i = 0; i < length; ++i) {
      accept(data[i]);
    }

    flush();
  }

  private void accept(byte b) {
    switch (parseState) {
      case Normal:
        if (b == IAC) {
          parseState = ParseState.FoundIAC;
        } else {
          byteArrayOutputStream.write(b);
        }
        break;
      case FoundIAC:
        if (b == SB) {
          parseState = ParseState.SBStart;
        } else if (b == WILL || b == WONT || b == DO || b == DONT) {
          parseState = ParseState.IACCommand;
        } else if (b == IAC) { // special escape sequence
          byteArrayOutputStream.write(b);
        } else {
          parseState = ParseState.Normal;
        }
        break;
      case IACCommand:
        parseState = ParseState.Normal;
        break;
      case SBStart:
        parseState = ParseState.SBValue;
        break;
      case SBValue:
        if (b == IAC) {
          parseState = ParseState.SBIAC;
        }
        break;
      case SBIAC:
        if (b == SE) {
          parseState = ParseState.Normal;
        } else {
          parseState = ParseState.SBValue;
        }
        break;
    }
  }

  private void flush() {
    if (byteArrayOutputStream.size() == 0) {
      return;
    }

    byte[] data = byteArrayOutputStream.toByteArray();
    byteArrayOutputStream.reset();

    consumer.accept(data, data.length);
  }
}
