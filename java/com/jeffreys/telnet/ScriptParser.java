package com.jeffreys.telnet;

import com.google.common.collect.ImmutableSet;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Parses incoming data for a #!script tag, and then captures the script to execute. */
final class ScriptParser implements BiConsumer<byte[], Integer> {
  private static final byte[] SCRIPT_LAUNCH_PREFIX = "#!script ".getBytes();
  private static final int MAX_PATH_LENGTH = 256;
  private static final ImmutableSet<Byte> backspaceCharacters =
      ImmutableSet.of((byte) '\b', (byte) 0x7F);

  /** The {@link Consumer} to call when script text is identified. */
  private final Consumer<String> onLaunchScript;
  /** Tracks the index in {@link #SCRIPT_LAUNCH_PREFIX} as input is received. */
  private int scriptLaunchIndex = 0;
  /** The builder used to capture the script name. */
  private StringBuilder scriptStringBuilder = null;

  ScriptParser(Consumer<String> onLaunchScript) {
    this.onLaunchScript = onLaunchScript;
  }

  @Override
  public void accept(byte[] buffer, Integer length) {
    for (int i = 0; i < length; ++i) {
      parseByte(buffer[i]);
    }
  }

  private void parseByte(byte b) {
    if (scriptStringBuilder != null) {
      parseScriptName(b);
      return;
    }

    if (b == SCRIPT_LAUNCH_PREFIX[scriptLaunchIndex]) {
      scriptLaunchIndex++;
      if (scriptLaunchIndex >= SCRIPT_LAUNCH_PREFIX.length) {
        scriptStringBuilder = new StringBuilder(MAX_PATH_LENGTH);
        scriptLaunchIndex = 0;
      }
    } else if (b == SCRIPT_LAUNCH_PREFIX[0]) {
      scriptLaunchIndex = 1;
    } else {
      scriptLaunchIndex = 0;
    }
  }

  private void parseScriptName(byte b) {
    if (b == '\r' || b == '\n') {
      // we're done, launch what script we have
      String script = scriptStringBuilder.toString().trim();
      if (script.length() > 0) {
        onLaunchScript.accept(script);
      }
      scriptStringBuilder = null;
      return;
    } else if (backspaceCharacters.contains(b)) {
      if (scriptStringBuilder.length() > 0) {
        scriptStringBuilder.deleteCharAt(scriptStringBuilder.length() - 1);
      }
      return;
    } else if (scriptStringBuilder.length() >= MAX_PATH_LENGTH) {
      scriptStringBuilder = null;
      return;
    }

    scriptStringBuilder.append((char) b);
  }
}
