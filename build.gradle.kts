plugins {
    kotlin("jvm") version "2.0.21" apply false
    kotlin("plugin.serialization") version "2.0.21" apply false
    id("com.google.devtools.ksp") version "2.0.21-1.0.25" apply false
}

group = "dev.vingle"
version = "1.0.0"

allprojects {
    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "maven-publish")
    
    group = rootProject.group
    version = rootProject.version
    
    configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(17))
        }
        withSourcesJar()
    }
    
    configure<PublishingExtension> {
        publications {
            create<MavenPublication>("maven") {
                from(components["java"])
                
                pom {
                    name.set("Vingle SpringDoc KDoc Provider")
                    description.set("A library for providing KDoc documentation to SpringDoc OpenAPI")
                    url.set("https://github.com/vingle/vingle-springdoc-kdoc-provider")
                    
                    licenses {
                        license {
                            name.set("MIT License")
                            url.set("https://opensource.org/licenses/MIT")
                        }
                    }
                    
                    developers {
                        developer {
                            id.set("vingle")
                            name.set("Vingle Team")
                            email.set("dev@vingle.net")
                        }
                    }
                    
                    scm {
                        connection.set("scm:git:git://github.com/vingle/vingle-springdoc-kdoc-provider.git")
                        developerConnection.set("scm:git:ssh://github.com:vingle/vingle-springdoc-kdoc-provider.git")
                        url.set("https://github.com/vingle/vingle-springdoc-kdoc-provider/tree/main")
                    }
                }
            }
        }
    }
} 