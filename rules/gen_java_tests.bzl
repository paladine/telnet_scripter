def _remove_java_extension(src):
  if not src.endswith(".java"):
    fail("{}: is not a Java file".format(src))

  return src[:-5]

def gen_java_tests(srcs, runtime_deps, **kwargs):
  for src in srcs:
    native.java_test(
      name = _remove_java_extension(src),
      runtime_deps = runtime_deps,
      **kwargs)

