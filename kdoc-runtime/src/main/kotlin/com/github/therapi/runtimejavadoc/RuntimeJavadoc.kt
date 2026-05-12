package com.github.therapi.runtimejavadoc

import dev.vingle.kdoc.RuntimeKDoc
import java.lang.reflect.Method

/**
 * Compatibility layer for therapi-runtime-javadoc
 */
object RuntimeJavadoc {
    
    /**
     * Get Javadoc documentation for a class
     */
    @JvmStatic
    fun getJavadoc(clazz: Class<*>): ClassJavadoc {
        val kDoc = RuntimeKDoc.getKDoc(clazz)
        return ClassJavadoc.fromKDoc(kDoc)
    }
    
    /**
     * Get Javadoc documentation for a class by fully qualified name
     */
    @JvmStatic
    fun getJavadoc(fullyQualifiedClassName: String): ClassJavadoc {
        val kDoc = RuntimeKDoc.getKDoc(fullyQualifiedClassName)
        return ClassJavadoc.fromKDoc(kDoc)
    }
    
    /**
     * Get Javadoc documentation for a specific method
     */
    @JvmStatic
    fun getJavadoc(method: Method): MethodJavadoc {
        val kDoc = RuntimeKDoc.getKDoc(method)
        return MethodJavadoc.fromKDoc(kDoc)
    }
    
    /**
     * Get Javadoc documentation for a field
     */
    @JvmStatic
    fun getJavadoc(field: java.lang.reflect.Field): FieldJavadoc {
        val kDoc = RuntimeKDoc.getKDoc(field)
        return FieldJavadoc.fromKDoc(kDoc)
    }
} 