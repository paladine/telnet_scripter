package com.jeffreys.telnet;

import com.google.auto.value.AutoValue;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

@AutoValue
abstract class Options {
  private static class Flags {
    @Option(name = "--remote_host", usage = "Remote host to connect to", required = true)
    public String remoteHost;

    @Option(name = "--remote_port", usage = "Remote host to connect to")
    public int remotePort = 23;

    @Option(name = "--local_port", usage = "Local port to listen on")
    public int localPort = 2112;
  }

  static Options parse(String[] args) {
    try {
      Flags flags = new Flags();
      CmdLineParser parser = new CmdLineParser(flags);
      parser.parseArgument(args);

      return new AutoValue_Options(flags.remoteHost, flags.remotePort, flags.localPort);
    } catch (CmdLineException e) {
      throw new IllegalArgumentException(e);
    }
  }

  abstract String getRemoteHost();

  abstract int getRemotePort();

  abstract int getLocalPort();
}
