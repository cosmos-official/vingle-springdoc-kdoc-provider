# Vingle SpringDoc KDoc Provider

A Kotlin library that provides KDoc documentation to SpringDoc OpenAPI, compatible with `therapi-runtime-javadoc` API.

## Overview

This library consists of two modules:
- **kdoc-runtime**: Runtime library for reading KDoc documentation
- **kdoc-processor**: KSP (Kotlin Symbol Processing) processor for extracting KDoc at compile time

## Features

- ✅ Extract KDoc comments from Kotlin classes and methods
- ✅ Compatible with `therapi-runtime-javadoc` API
- ✅ Support for SpringDoc OpenAPI integration
- ✅ Automatic JSON generation for runtime access
- ✅ Incremental processing with caching

## Installation

### JitPack

Add the JitPack repository to your `build.gradle.kts`:

```kotlin
repositories {
    maven { url = uri("https://jitpack.io") }
}
```

Add the dependencies:

```kotlin
dependencies {
    implementation("com.github.cosmos-official.vingle-springdoc-kdoc-provider:kdoc-runtime:v1.0.2")
    ksp("com.github.cosmos-official.vingle-springdoc-kdoc-provider:kdoc-processor:v1.0.2")
}
```

## Usage

### 1. Add KDoc to your controllers

```kotlin
/**
 * User management controller
 * 
 * This controller handles all user-related operations including
 * user registration, authentication, and profile management.
 */
@RestController
@RequestMapping("/api/users")
class UserController {

    /**
     * Get user by ID
     * 
     * @param id The unique identifier of the user
     * @return User information if found
     * @throws UserNotFoundException when user with given ID doesn't exist
     */
    @GetMapping("/{id}")
    fun getUser(@PathVariable id: Long): UserResponse {
        // implementation
    }
}
```

### 2. (Optional) Configure KSP processor

Add processor options in your `build.gradle.kts`:

```kotlin
ksp {
    arg("kdoc.packages", "com.yourcompany.api,com.yourcompany.controller")
    arg("kdoc.disable-cache", "false")
    arg("kdoc.force-regenerate", "false")
}
```

### 3. (Optional) Access documentation at runtime
This process is automatically done by SpringDoc when you have dependency.
Refer [this](https://springdoc.org/#javadoc-support)

```kotlin
import dev.vingle.kdoc.RuntimeKDoc

// Get documentation for a class
val classDoc = RuntimeKDoc.getKDoc(UserController::class.java)

// Access method documentation
val methodDoc = classDoc.methods.find { it.name == "getUser" }
println(methodDoc?.comment?.text) // "Get user by ID"
```

## Configuration Options

| Option | Description | Default |
|--------|-------------|---------|
| `kdoc.packages` | Comma-separated list of packages to process | All packages |
| `kdoc.disable-cache` | Disable incremental processing | `false` |
| `kdoc.force-regenerate` | Force regeneration of all documentation | `false` |

## therapi-runtime-javadoc Compatibility

This library provides a compatibility layer with `therapi-runtime-javadoc`:

```kotlin
import com.github.therapi.runtimejavadoc.RuntimeJavadoc

// Use familiar therapi API
val classDoc = RuntimeJavadoc.getJavadoc(UserController::class.java)
```

## Build Requirements

- Kotlin 2.0.21+
- Java 17+
- Gradle 8.0+

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests
5. Submit a pull request

## License

MIT License - see [LICENSE](LICENSE) file for details. 
