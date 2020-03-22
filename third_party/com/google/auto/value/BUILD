java_library(
    name = "value",
    exported_plugins = [
        ":auto_value_plugin"
    ],
    exports = [
        "@maven//:com_google_auto_value_auto_value",
        "@maven//:com_google_auto_value_auto_value_annotations",
    ],
    licenses = [
        "unencumbered"
    ],
    visibility = [
        "//visibility:public"
    ]
)
java_plugin(
    name = "auto_value_plugin",
    deps = [
        "@maven//:com_google_auto_value_auto_value",
    ],
    licenses = [
        "unencumbered"
    ],
    processor_class = "com.google.auto.value.processor.AutoValueProcessor",
    visibility = [
        "//visibility:public"
    ]
)



