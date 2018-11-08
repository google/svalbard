workspace(name="svalbard")

#-----------------------------------------------------------------------------
# proto
#-----------------------------------------------------------------------------
# proto_library, cc_proto_library and java_proto_library rules implicitly depend
# on @com_google_protobuf//:proto, @com_google_protobuf//:cc_toolchain and
# @com_google_protobuf//:java_toolchain, respectively.
# This statement defines the @com_google_protobuf repo.
http_archive(
    name = "com_google_protobuf",
    strip_prefix = "protobuf-3.6.0",
    urls = ["https://github.com/google/protobuf/archive/v3.6.0.zip"],
    sha256 = "e514c2e613dc47c062ea8df480efeec368ffbef98af0437ac00cdaadcb0d80d2",
)

# java_lite_proto_library rules implicitly depend on
# @com_google_protobuf_javalite//:javalite_toolchain, which is the JavaLite proto
# runtime (base classes and common utilities).
http_archive(
    name = "com_google_protobuf_javalite",
    strip_prefix = "protobuf-javalite",
    urls = ["https://github.com/google/protobuf/archive/javalite.zip"],
    sha256 = "38458deb90db61c054b708e141544c32863ab14a8747710ba3ee290d9b6dab92",
)

#-----------------------------------------------------------------------------
# java
#-----------------------------------------------------------------------------

load("@bazel_tools//tools/build_defs/repo:java.bzl", "java_import_external")

java_import_external(
    name = "com_google_guava",
    licenses = ["notice"],  # The Apache Software License, Version 2.0
    jar_sha256 = "36a666e3b71ae7f0f0dca23654b67e086e6c93d192f60ba5dfd5519db6c288c8",
    jar_urls = [
        "https://maven.ibiblio.org/maven2/com/google/guava/guava/20.0/guava-20.0.jar",
        "https://repo1.maven.org/maven2/com/google/guava/guava/20.0/guava-20.0.jar",
    ],
)
java_import_external(
    name = "junit",
    licenses = ["reciprocal"],  # Eclipse Public License 1.0
    jar_sha256 = "59721f0805e223d84b90677887d9ff567dc534d7c502ca903c0c2b17f05c116a",
    jar_urls = [
        "https://repo1.maven.org/maven2/junit/junit/4.12/junit-4.12.jar",
        "https://maven.ibiblio.org/maven2/junit/junit/4.12/junit-4.12.jar",
    ],
    deps = ["@org_hamcrest_core"],
)

java_import_external(
    name = "org_hamcrest_core",
    licenses = ["notice"],  # New BSD License
    jar_sha256 = "66fdef91e9739348df7a096aa384a5685f4e875584cce89386a7a47251c4d8e9",
    jar_urls = [
        "https://repo1.maven.org/maven2/org/hamcrest/hamcrest-core/1.3/hamcrest-core-1.3.jar",
        "https://maven.ibiblio.org/maven2/org/hamcrest/hamcrest-core/1.3/hamcrest-core-1.3.jar",
    ],
)

java_import_external(
    name = "commons_codec",
    licenses = ["notice"],  # The Apache Software License, Version 2.0
    jar_sha256 = "ad19d2601c3abf0b946b5c3a4113e226a8c1e3305e395b90013b78dd94a723ce",
    jar_urls = [
        "https://maven.ibiblio.org/maven2/commons-codec/commons-codec/1.9/commons-codec-1.9.jar",
        "https://repo1.maven.org/maven2/commons-codec/commons-codec/1.9/commons-codec-1.9.jar",
    ],
)

java_import_external(
    name = "commons_io",
    licenses = ["notice"],  # The Apache Software License, Version 2.0
    jar_sha256 = "f877d304660ac2a142f3865badfc971dec7ed73c747c7f8d5d2f5139ca736513",
    jar_urls = [
        "https://maven.ibiblio.org/maven2/commons-io/commons-io/2.6/commons-io-2.6.jar",
        "https://repo1.maven.org/maven2/commons-io/commons-io/2.6/commons-io-2.6.jar",
    ],
)

java_import_external(
    name = "commons_logging",
    licenses = ["notice"],  # The Apache Software License, Version 2.0
    jar_sha256 = "daddea1ea0be0f56978ab3006b8ac92834afeefbd9b7e4e6316fca57df0fa636",
    jar_urls = [
        "https://maven.ibiblio.org/maven2/commons-logging/commons-logging/1.2/commons-logging-1.2.jar",
        "https://repo1.maven.org/maven2/commons-logging/commons-logging/1.2/commons-logging-1.2.jar",
    ],
)

java_import_external(
    name = "org_apache_httpclient",
    licenses = ["notice"],  # Apache License, Version 2.0
    jar_sha256 = "0dffc621400d6c632f55787d996b8aeca36b30746a716e079a985f24d8074057",
    jar_urls = [
        "https://repo1.maven.org/maven2/org/apache/httpcomponents/httpclient/4.5.2/httpclient-4.5.2.jar",
        "https://maven.ibiblio.org/maven2/org/apache/httpcomponents/httpclient/4.5.2/httpclient-4.5.2.jar",
    ],
    deps = [
        "@org_apache_httpcore",
        "@commons_logging",
        "@commons_codec",
    ],
)

java_import_external(
    name = "org_apache_httpcore",
    licenses = ["notice"],  # Apache License, Version 2.0
    jar_sha256 = "f7bc09dc8a7003822d109634ffd3845d579d12e725ae54673e323a7ce7f5e325",
    jar_urls = [
        "https://maven.ibiblio.org/maven2/org/apache/httpcomponents/httpcore/4.4.4/httpcore-4.4.4.jar",
        "https://repo1.maven.org/maven2/org/apache/httpcomponents/httpcore/4.4.4/httpcore-4.4.4.jar",
    ],
)

#-----------------------------------------------------------------------------
# go
#-----------------------------------------------------------------------------
http_archive(
    name = "io_bazel_rules_go",
    url = "https://github.com/bazelbuild/rules_go/releases/download/0.10.1/rules_go-0.10.1.tar.gz",
    sha256 = "4b14d8dd31c6dbaf3ff871adcd03f28c3274e42abc855cb8fb4d01233c0154dc",
)

http_archive(
    name = "bazel_gazelle",
    urls = ["https://github.com/bazelbuild/bazel-gazelle/releases/download/0.14.0/bazel-gazelle-0.14.0.tar.gz"],
    sha256 = "c0a5739d12c6d05b6c1ad56f2200cb0b57c5a70e03ebd2f7b87ce88cabf09c7b",
)

load("@io_bazel_rules_go//go:def.bzl", "go_rules_dependencies", "go_register_toolchains")
go_rules_dependencies()
go_register_toolchains()

load("@bazel_gazelle//:deps.bzl", "gazelle_dependencies")
gazelle_dependencies()

load("@bazel_gazelle//:deps.bzl", "go_repository")

go_repository(
    name = "bbolt_db",
    importpath = "github.com/etcd-io/bbolt/bolt",
    urls = ["https://github.com/etcd-io/bbolt/archive/v1.3.1-etcd.8.zip"],
    sha256 = "bf190f6b74086f73fa4621c5b7557022aaf4016613707cba76e3e0f3586b988e",
    strip_prefix = "bbolt-1.3.1-etcd.8",
    type = "zip",
)

#-----------------------------------------------------------------------------
# sh
#-----------------------------------------------------------------------------
new_http_archive(
    name = "org_python_pypi_portpicker",
    urls = [
        "https://mirror.bazel.build/pypi.python.org/packages/96/48/0e1f20fdc0b85cc8722284da3c5b80222ae4036ad73210a97d5362beaa6d/portpicker-1.1.1.tar.gz",
        "https://pypi.python.org/packages/96/48/0e1f20fdc0b85cc8722284da3c5b80222ae4036ad73210a97d5362beaa6d/portpicker-1.1.1.tar.gz",
    ],
    sha256 = "2f88edf7c6406034d7577846f224aff6e53c5f4250e3294b1904d8db250f27ec",
    strip_prefix = "portpicker-1.1.1/src",
    build_file = "third_party/portpicker.BUILD.bazel",
)
