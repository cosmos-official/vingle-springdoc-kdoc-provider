package dev.vingle.kdoc

import dev.vingle.kdoc.model.ClassKDoc
import dev.vingle.kdoc.model.FieldKDoc
import kotlinx.serialization.json.Json
import org.jetbrains.annotations.TestOnly
import java.lang.reflect.Field
import java.lang.reflect.Method
import kotlin.reflect.KClass

/**
 * Main entry point for reading KDoc at runtime, compatible with therapi-runtime-javadoc API
 */
object RuntimeKDoc {
    
    private val json = Json { ignoreUnknownKeys = true }
    private val classKDocCache = mutableMapOf<String, ClassKDoc>()

    /** Type mapping for primitive vs boxed types - organized in pairs for easier maintenance */
    private val primitiveToBoxed = mapOf(
        "long" to "Long", "boolean" to "Boolean", "int" to "Int",
        "double" to "Double", "float" to "Float", "byte" to "Byte",
        "short" to "Short", "char" to "Char",

        "long[]" to "LongArray", "boolean[]" to "BooleanArray", "int[]" to "IntArray",
        "double[]" to "DoubleArray", "float[]" to "FloatArray", "byte[]" to "ByteArray",
        "short[]" to "ShortArray", "char[]" to "CharArray",
    )
    private val boxedToPrimitive = primitiveToBoxed.entries.associate { it.value to it.key }

    /** Additional Kotlin-Java type mappings */
    private val kotlinToJavaTypes = mapOf(
        "Int" to "Integer", "Long" to "Long", "Boolean" to "Boolean",
        "Double" to "Double", "Float" to "Float", "Byte" to "Byte",
        "Short" to "Short", "Char" to "Character",

        "IntArray" to "int[]", "LongArray" to "long[]", "BooleanArray" to "boolean[]",
        "DoubleArray" to "double[]", "FloatArray" to "float[]", "ByteArray" to "byte[]",
        "ShortArray" to "short[]", "CharArray" to "char[]",
    )
    private val javaToKotlinTypes = kotlinToJavaTypes.entries.associate { it.value to it.key }
    
    /**
     * Get KDoc documentation for a class
     */
    fun getKDoc(clazz: Class<*>): ClassKDoc {
        return getKDoc(fullyQualifiedClassName = clazz.kotlin.qualifiedName ?: clazz.name)
    }

    /**
     * Get KDoc documentation for a class by its fully qualified name.
     */
    fun getKDoc(fullyQualifiedClassName: String): ClassKDoc {
        // Check cache first
        classKDocCache[fullyQualifiedClassName]?.let { return it }
        
        val resourcePath = "/kdoc/${fullyQualifiedClassName.replace('.', '/')}.json"
        val resource = RuntimeKDoc::class.java.getResourceAsStream(resourcePath)
            ?: return createEmptyClassKDoc(fullyQualifiedClassName)
        
        val classKDoc = try {
            val jsonText = resource.use { it.readBytes().decodeToString() }
            json.decodeFromString<ClassKDoc>(jsonText)
        } catch (exception: Exception) {
            if (isDebugEnabled) // Log specific exception types if needed for debugging
                println("DEBUG: Failed to parse documentation for class '$fullyQualifiedClassName': $exception")
            createEmptyClassKDoc(fullyQualifiedClassName)
        }
        
        // Cache the result
        classKDocCache[fullyQualifiedClassName] = classKDoc
        return classKDoc
    }
    
    /**
     * Get KDoc documentation for a specific method
     */
    fun getKDoc(method: Method): dev.vingle.kdoc.model.MethodKDoc {
        val classKDoc = getKDoc(method.declaringClass)
        val paramTypeNames = method.parameterTypes.map { it.simpleName }

        if (isDebugEnabled) {
            println("DEBUG: Looking for method ${method.name} with param types: $paramTypeNames")
            println("DEBUG: Available methods in ${method.declaringClass.simpleName}:")
            classKDoc.methods.forEach { methodKDoc ->
                println("  - ${methodKDoc.name}(${methodKDoc.paramTypes.joinToString(", ")})")
            }
        }

        val found = classKDoc.methods.find { methodKDoc ->
            val nameMatch = methodKDoc.name == method.name
            val sizeMatch = methodKDoc.paramTypes.size == paramTypeNames.size
            val typeMatch = isParameterTypesMatch(methodKDoc.paramTypes, paramTypeNames)

            if (isDebugEnabled && nameMatch) {
                println("DEBUG: Checking ${methodKDoc.name}: nameMatch=$nameMatch, sizeMatch=$sizeMatch, typeMatch=$typeMatch")
                if (sizeMatch && !typeMatch) {
                    println("DEBUG: Type mismatch details:")
                    methodKDoc.paramTypes.zip(paramTypeNames).forEach { (kdoc, reflection) ->
                        val match = isTypeMatch(kdoc, reflection)
                        println("    $kdoc vs $reflection = $match")
                    }
                }
            }
            
            nameMatch && sizeMatch && typeMatch
        }

        return found ?: dev.vingle.kdoc.model.MethodKDoc.empty(method.name, paramTypeNames)
    }
    
    /**
     * Get KDoc documentation for a Kotlin class
     */
    fun getKDoc(kclass: KClass<*>): ClassKDoc {
        return getKDoc(kclass.java)
    }

    /**
     * Get KDoc documentation for a specific field.
     */
    fun getKDoc(field: Field): FieldKDoc {
        val classKDoc = getKDoc(field.declaringClass)
        return classKDoc.fields.singleOrNull { it.name == field.name } ?: FieldKDoc.empty(field.name)
    }

    /**
     * Check if parameter types match, considering primitive vs boxed types
     */
    private fun isParameterTypesMatch(kdocTypes: List<String>, reflectionTypes: List<String>): Boolean {
        return kdocTypes.zip(reflectionTypes).all { (kdocType, reflectionType) ->
            isTypeMatch(kdocType, reflectionType)
        }
    }
    
    /**
     * Check if two types match, considering all possible type mappings
     */
    private fun isTypeMatch(kdocType: String, reflectionType: String): Boolean {
        // Direct match
        if (kdocType == reflectionType) return true
        
        // Primitive <-> Boxed type mapping
        if (primitiveToBoxed[kdocType] == reflectionType) return true
        if (boxedToPrimitive[kdocType] == reflectionType) return true
        
        // Kotlin <-> Java type mapping (for nullable types)
        if (kotlinToJavaTypes[kdocType] == reflectionType) return true
        if (javaToKotlinTypes[kdocType] == reflectionType) return true
        
        // Simple name matching (for enums and classes)
        val kdocSimpleName = kdocType.substringAfterLast('.')
        val reflectionSimpleName = reflectionType.substringAfterLast('.')
        if (kdocSimpleName == reflectionSimpleName) return true
        if (kdocSimpleName == reflectionType) return true
        if (kdocType == reflectionSimpleName) return true
        
        // Ends with matching (for nested classes)
        if (kdocType.endsWith(reflectionType)) return true
        if (reflectionType.endsWith(kdocType)) return true
        
        return false
    }
    
    /**
     * Create empty ClassKDoc for classes without documentation
     */
    private fun createEmptyClassKDoc(className: String): ClassKDoc {
        return ClassKDoc(
            name = className,
            comment = dev.vingle.kdoc.model.CommentKDoc.empty(),
        )
    }

    /**
     * This function can be used to manually clear the cache in order to free up some memory.
     *
     * ### Example
     * One can provide their own `SpringDocJavadocProvider` implementation which calls this method.
     * ```kotlin
     * class MySpringDocJavadocProvider : SpringDocJavadocProvider {
     *    override fun clearCache() {
     *       super.clearCache()
     *       RuntimeKDoc.clearCache()
     *    }
     * }
     * ```
     */
    fun clearCache(): Unit = classKDocCache.clear()

    /** This method is used to test the [clearCache] function. */
    @get:TestOnly
    internal val currentCacheSize get() = classKDocCache.size
}

/** Debug logging - can be enabled for troubleshooting */
private val isDebugEnabled = System.getProperty("kdoc.debug", "false").toBoolean()
