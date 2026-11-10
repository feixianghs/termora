pluginManagement {
    repositories {
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        maven("https://maven.aliyun.com/repository/public")
        maven("https://mirrors.huaweicloud.com/repository/maven")
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
rootProject.name = "termora"

include("plugins:s3")
include("plugins:oss")
include("plugins:cos")
include("plugins:obs")
include("plugins:ftp")
include("plugins:bg")
include("plugins:sync")
include("plugins:migration")
include("plugins:editor")
include("plugins:geo")
include("plugins:webdav")
include("plugins:smb")
include("plugins:serial")
include("plugins:vnc")
