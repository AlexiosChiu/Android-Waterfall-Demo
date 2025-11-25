pluginManagement {
    repositories {
        // 1. 阿里云系列（全量同步 MavenCentral、Google、Gradle Plugin）
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
        maven { url = uri("https://maven.aliyun.com/repository/central") }
        // 2. 华为云
        maven { url = uri("https://repo.huaweicloud.com/repository/maven/") }
        // 3. 腾讯云
        maven { url = uri("https://mirrors.cloud.tencent.com/nexus/repository/maven-public/") }
        // 4. 网易
        maven { url = uri("https://mirrors.163.com/maven/repository/maven-public/") }

        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // 1. 阿里云系列（全量同步 MavenCentral、Google、Gradle Plugin）
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
        maven { url = uri("https://maven.aliyun.com/repository/central") }
        // 2. 华为云
        maven { url = uri("https://repo.huaweicloud.com/repository/maven/") }
        // 3. 腾讯云
        maven { url = uri("https://mirrors.cloud.tencent.com/nexus/repository/maven-public/") }
        // 4. 网易
        maven { url = uri("https://mirrors.163.com/maven/repository/maven-public/") }

        google()
        mavenCentral()
    }
}

rootProject.name = "Waterfall"
include(":app")
 