package com.jeffreys.telnet;

import java.io.IOException;

@FunctionalInterface
interface ProcessLauncher {
  Process start(ProcessBuilder processBuilder) throws IOException;
}
