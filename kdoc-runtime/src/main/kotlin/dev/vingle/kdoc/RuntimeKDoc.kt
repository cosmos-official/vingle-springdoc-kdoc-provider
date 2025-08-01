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
    
    // Type mapping for primitive vs boxed types
    private val typeMapping = mapOf(
        "Long" to "long", "long" to "Long",
        "Boolean" to "boolean", "boolean" to "Boolean", 
        "Int" to "int", "int" to "Int",
        "Double" to "double", "double" to "Double",
        "Float" to "float", "float" to "Float",
        "Byte" to "byte", "byte" to "Byte",
        "Short" to "short", "short" to "Short",
        "Char" to "char", "char" to "Char"
    )
    
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

        return classKDoc.methods.find { methodKDoc ->
            methodKDoc.name == method.name &&
            methodKDoc.paramTypes.size == paramTypeNames.size &&
            isParameterTypesMatch(methodKDoc.paramTypes, paramTypeNames)
        } ?: dev.vingle.kdoc.model.MethodKDoc.empty(method.name, paramTypeNames)
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
            kdocType == reflectionType || 
            typeMapping[kdocType] == reflectionType ||
            kdocType.endsWith(reflectionType) || 
            reflectionType.endsWith(kdocType)
        }
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