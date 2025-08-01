package com.github.therapi.runtimejavadoc

import dev.vingle.kdoc.model.ClassKDoc

/**
 * Compatibility layer for therapi ClassJavadoc
 */
class ClassJavadoc private constructor(
    private val kDoc: ClassKDoc
) {
    
    fun getName(): String = kDoc.name
    
    fun getComment(): Comment = Comment.fromKDoc(kDoc.comment)
    
    fun isEmpty(): Boolean = kDoc.isEmpty()
    
    fun getMethods(): List<MethodJavadoc> = kDoc.methods.map { MethodJavadoc.fromKDoc(it) }
    
    fun getConstructors(): List<MethodJavadoc> = kDoc.constructors.map { MethodJavadoc.fromKDoc(it) }
    
    fun getSeeAlso(): List<SeeAlsoJavadoc> = kDoc.seeAlso.map { SeeAlsoJavadoc.fromKDoc(it) }
    
    fun getOther(): List<OtherJavadoc> = kDoc.other.map { OtherJavadoc.fromKDoc(it) }
    
    /**
     * Get record components (for compatibility with newer therapi versions)
     * Returns empty list as Kotlin data classes don't have Java record components
     */
    fun getRecordComponents(): List<ParamJavadoc> = emptyList()
    
    companion object {
        @JvmStatic
        fun fromKDoc(kDoc: ClassKDoc): ClassJavadoc = ClassJavadoc(kDoc)
    }
} 