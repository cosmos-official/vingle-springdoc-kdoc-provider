package com.github.therapi.runtimejavadoc

import dev.vingle.kdoc.model.*

/**
 * Compatibility layer for therapi Comment
 */
class Comment private constructor(
    private val kDoc: CommentKDoc
) {
    
    fun isEmpty(): Boolean = kDoc.isEmpty()
    
    fun getText(): String = kDoc.text
    
    companion object {
        @JvmStatic
        fun fromKDoc(kDoc: CommentKDoc): Comment = Comment(kDoc)
    }
}

/**
 * Compatibility layer for therapi MethodJavadoc
 */
class MethodJavadoc private constructor(
    private val kDoc: MethodKDoc
) {
    
    fun getName(): String = kDoc.name
    
    fun getParamTypes(): List<String> = kDoc.paramTypes
    
    fun getComment(): Comment = Comment.fromKDoc(kDoc.comment)
    
    fun getParams(): List<ParamJavadoc> = kDoc.params.map { ParamJavadoc.fromKDoc(it) }
    
    fun getReturns(): Comment = Comment.fromKDoc(kDoc.returns)
    
    fun getThrows(): List<ThrowsJavadoc> = kDoc.throws.map { ThrowsJavadoc.fromKDoc(it) }
    
    fun getSeeAlso(): List<SeeAlsoJavadoc> = kDoc.seeAlso.map { SeeAlsoJavadoc.fromKDoc(it) }
    
    fun getOther(): List<OtherJavadoc> = kDoc.other.map { OtherJavadoc.fromKDoc(it) }
    
    fun isConstructor(): Boolean = kDoc.isConstructor
    
    companion object {
        @JvmStatic
        fun fromKDoc(kDoc: MethodKDoc): MethodJavadoc = MethodJavadoc(kDoc)
    }
}

/**
 * Compatibility layer for therapi ParamJavadoc
 */
class ParamJavadoc private constructor(
    private val kDoc: ParamKDoc
) {
    
    fun getName(): String = kDoc.name
    
    fun getComment(): Comment = Comment.fromKDoc(kDoc.comment)
    
    companion object {
        @JvmStatic
        fun fromKDoc(kDoc: ParamKDoc): ParamJavadoc = ParamJavadoc(kDoc)
    }
}

/**
 * Compatibility layer for therapi ThrowsJavadoc
 */
class ThrowsJavadoc private constructor(
    private val kDoc: ThrowsKDoc
) {
    
    fun getName(): String = kDoc.name
    
    fun getComment(): Comment = Comment.fromKDoc(kDoc.comment)
    
    companion object {
        @JvmStatic
        fun fromKDoc(kDoc: ThrowsKDoc): ThrowsJavadoc = ThrowsJavadoc(kDoc)
    }
}

/**
 * Compatibility layer for therapi SeeAlsoJavadoc
 */
class SeeAlsoJavadoc private constructor(
    private val kDoc: SeeAlsoKDoc
) {
    
    fun getLink(): String = kDoc.link
    
    companion object {
        @JvmStatic
        fun fromKDoc(kDoc: SeeAlsoKDoc): SeeAlsoJavadoc = SeeAlsoJavadoc(kDoc)
    }
}

/**
 * Compatibility layer for therapi OtherJavadoc
 */
class OtherJavadoc private constructor(
    private val kDoc: OtherKDoc
) {
    
    fun getName(): String = kDoc.name
    
    fun getComment(): Comment = Comment.fromKDoc(kDoc.comment)
    
    companion object {
        @JvmStatic
        fun fromKDoc(kDoc: OtherKDoc): OtherJavadoc = OtherJavadoc(kDoc)
    }
}

/**
 * Compatibility layer for therapi FieldJavadoc
 */
class FieldJavadoc private constructor(
    private val name: String,
    private val comment: Comment
) {
    
    fun getName(): String = name
    
    fun getComment(): Comment = comment
    
    companion object {
        @JvmStatic
        fun empty(fieldName: String): FieldJavadoc = FieldJavadoc(
            name = fieldName,
            comment = Comment.fromKDoc(CommentKDoc.empty())
        )
    }
}

/**
 * Compatibility layer for therapi CommentFormatter
 */
class CommentFormatter {
    
    fun format(comment: Comment): String {
        return comment.getText()
    }
} 