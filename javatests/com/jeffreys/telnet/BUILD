load("//rules:gen_java_tests.bzl", "gen_java_tests")

java_library(
  name = "tests",
  srcs = glob(["*.java"]),
  deps = [
    "@maven//:com_google_truth_truth",
    "@maven//:com_google_truth_extensions_truth_java8_extension",
    "@maven//:junit_junit",
    "//java/com/jeffreys/junit",
    "//java/com/jeffreys/telnet:lib",
    "//third_party/com/google/auto/value",
    "@maven//:com_google_guava_guava",
    "@maven//:org_mockito_mockito_core",
  ],
)

gen_java_tests(
  srcs = glob(["*.java"]),
  runtime_deps = [":tests"],
  size = "small",
)
