package com.jeffreys.telnet;

public class Main {
  public static void main(String[] args) throws Exception {
    Options options = Options.parse(args);
    new Interceptor(options).run();
  }
}
