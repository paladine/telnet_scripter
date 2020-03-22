package(default_visibility = ["//javatests/com/jeffreys/telnet:__subpackages__"])

BINARY_SRC = ["Main.java"]

java_library(
    name = "lib",
    srcs = glob(["*.java"], exclude = BINARY_SRC),
    deps = [
      "//third_party/com/google/auto/value",
      "@org_kohsuke_arg4j//:args4j",
      "@maven//:com_google_code_findbugs_jsr305",
      "@maven//:com_google_guava_guava",
      "@maven//:com_google_flogger_flogger",
      "@maven//:com_google_flogger_flogger_system_backend",
    ],
)

java_binary(
    name = "TelnetScripter",
    srcs = BINARY_SRC,
    main_class = "com.jeffreys.telnet.Main",
    deps = [
      ":lib",
    ],
)
