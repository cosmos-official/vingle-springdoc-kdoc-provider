# Vingle SpringDoc KDoc Provider

A Kotlin library that provides KDoc documentation to SpringDoc OpenAPI, compatible with `therapi-runtime-javadoc` API.

## Overview

This library consists of two modules:
- **kdoc-runtime**: Runtime library for reading KDoc documentation
- **kdoc-processor**: KSP (Kotlin Symbol Processing) processor for extracting KDoc at compile time

## Features

- ✅ Extract KDoc comments as JSON from Kotlin classes and methods, thus accessible at runtime.
- ✅ Incremental processing with caching
- ✅ Support for SpringDoc OpenAPI integration
- ✅ Compatible with `therapi-runtime-javadoc` API (1-on-1 replaceable)

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
    implementation("com.github.cosmos-official.vingle-springdoc-kdoc-provider:kdoc-runtime:v1.0.6")
    ksp("com.github.cosmos-official.vingle-springdoc-kdoc-provider:kdoc-processor:v1.0.6")
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

### 2. (Optional) Access comments at runtime
This process is automatically done by SpringDoc when you have dependency.
Refer to [this](https://springdoc.org/#javadoc-support)

```kotlin
import dev.vingle.kdoc.RuntimeKDoc

// Get documentation for a class
val classDoc = RuntimeKDoc.getKDoc(UserController::class.java)

// Access method documentation
val methodDoc = classDoc.methods.find { it.name == "getUser" }
println(methodDoc?.comment?.text) // "Get user by ID"
```

## therapi-runtime-javadoc Compatibility

This library provides a compatibility layer with `therapi-runtime-javadoc`:

```kotlin
import com.github.therapi.runtimejavadoc.RuntimeJavadoc

// Use familiar therapi API
val classDoc = RuntimeJavadoc.getJavadoc(UserController::class.java)
```

## Build Requirements

- Kotlin 2.2.20+
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
