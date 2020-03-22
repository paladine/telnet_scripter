load("@bazel_tools//tools/build_defs/repo:git.bzl", "git_repository", "new_git_repository")
load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive")

new_git_repository(
    name = "org_kohsuke_arg4j",
    remote = "https://github.com/kohsuke/args4j.git",
    tag = "args4j-site-2.33",
    build_file_content = """
java_library(
  name = "args4j",
  visibility = ["//visibility:public"],
  srcs = glob(["args4j/src/org/kohsuke/args4j/**/*.java"]),
  resources = glob(["args4j/src/org/kohsuke/args4j/*.properties"]),
  deps = [],
)""",
)

git_repository(
    name = "rules_jvm_external",
    commit = "d442b54",
    remote = "https://github.com/bazelbuild/rules_jvm_external",
)

load("@rules_jvm_external//:defs.bzl", "maven_install")

maven_install(
    artifacts = [
        "com.google.inject:guice:4.2.2",
        "com.google.inject.extensions:guice-testlib:4.2.2",

        "com.google.flogger:flogger:0.4",
        "com.google.flogger:flogger-system-backend:0.4",

        "junit:junit:4.13",

        "org.mockito:mockito-core:3.3.3",

        "com.google.auto.value:auto-value:1.7",
        "com.google.auto.value:auto-value-annotations:1.7",

        "com.google.guava:guava:28.2-jre",
        "com.google.guava:guava:28.2-android",

        "com.google.truth:truth:1.0.1",
        "com.google.truth.extensions:truth-java8-extension:1.0.1",

        "javax.inject:javax.inject:1",

        "com.google.code.findbugs:jsr305:3.0.2",
    ],
    repositories = [
        "https://jcenter.bintray.com",
        "https://maven.google.com",
        "https://repo1.maven.org/maven2",
    ],
)

