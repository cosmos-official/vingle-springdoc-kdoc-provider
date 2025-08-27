package dev.vingle.kdoc

import dev.vingle.kdoc.model.ClassKDoc
import kotlinx.serialization.json.Json
import java.lang.reflect.Method
import kotlin.reflect.KClass

/**
 * Main entry point for reading KDoc at runtime, compatible with therapi-runtime-javadoc API
 */
object RuntimeKDoc {
    
    private val json = Json { ignoreUnknownKeys = true }
    private val classKDocCache = mutableMapOf<String, ClassKDoc>()
    
    // Type mapping for primitive vs boxed types - organized in pairs for easier maintenance
    private val primitiveToBoxed = mapOf(
        "long" to "Long", "boolean" to "Boolean", "int" to "Int", 
        "double" to "Double", "float" to "Float", "byte" to "Byte",
        "short" to "Short", "char" to "Char"
    )
    
    private val boxedToPrimitive = primitiveToBoxed.entries.associate { it.value to it.key }
    
    // Additional Kotlin-Java type mappings
    private val kotlinToJavaTypes = mapOf(
        "Int" to "Integer", "Long" to "Long", "Boolean" to "Boolean",
        "Double" to "Double", "Float" to "Float", "Byte" to "Byte",
        "Short" to "Short", "Char" to "Character"
    )
    
    private val javaToKotlinTypes = kotlinToJavaTypes.entries.associate { it.value to it.key }
    
    /**
     * Get KDoc documentation for a class
     */
    fun getKDoc(clazz: Class<*>): ClassKDoc {
        return getKDoc(clazz.name)
    }
    
    /**
     * Get KDoc documentation for a class by fully qualified name
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
        } catch (e: Exception) {
            // Log specific exception types if needed for debugging
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

        // Debug logging - can be enabled for troubleshooting
        val debugEnabled = System.getProperty("kdoc.debug", "false").toBoolean()
        
        if (debugEnabled) {
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
            
            if (debugEnabled && nameMatch) {
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
            comment = dev.vingle.kdoc.model.CommentKDoc.empty()
        )
    }
} 