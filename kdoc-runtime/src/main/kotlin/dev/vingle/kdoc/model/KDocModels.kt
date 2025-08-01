package dev.vingle.kdoc.model

import kotlinx.serialization.Serializable

/**
 * Represents a KDoc comment for a class or interface
 */
@Serializable
data class ClassKDoc(
    val name: String,
    val comment: CommentKDoc,
    val methods: List<MethodKDoc> = emptyList(),
    val constructors: List<MethodKDoc> = emptyList(),
    val seeAlso: List<SeeAlsoKDoc> = emptyList(),
    val other: List<OtherKDoc> = emptyList()
) {
    fun isEmpty(): Boolean = comment.isEmpty() && methods.isEmpty() && constructors.isEmpty()
}

/**
 * Represents a KDoc comment for a method or function
 */
@Serializable
data class MethodKDoc(
    val name: String,
    val paramTypes: List<String>,
    val comment: CommentKDoc,
    val params: List<ParamKDoc> = emptyList(),
    val returns: CommentKDoc = CommentKDoc.empty(),
    val throws: List<ThrowsKDoc> = emptyList(),
    val seeAlso: List<SeeAlsoKDoc> = emptyList(),
    val other: List<OtherKDoc> = emptyList(),
    val isConstructor: Boolean = false
) {
    companion object {
        fun empty(methodName: String, paramTypes: List<String>): MethodKDoc = MethodKDoc(
            name = methodName,
            paramTypes = paramTypes,
            comment = CommentKDoc.empty()
        )
    }
}

/**
 * Represents a KDoc comment content
 */
@Serializable
data class CommentKDoc(
    val text: String,
    val inlineTags: List<InlineTagKDoc> = emptyList()
) {
    fun isEmpty(): Boolean = text.isBlank() && inlineTags.isEmpty()
    
    companion object {
        fun empty() = CommentKDoc("")
    }
}

/**
 * Represents an inline tag in KDoc comment
 */
@Serializable
data class InlineTagKDoc(
    val name: String,
    val content: String
)

/**
 * Represents a @param tag in KDoc
 */
@Serializable
data class ParamKDoc(
    val name: String,
    val comment: CommentKDoc
)

/**
 * Represents a @throws tag in KDoc
 */
@Serializable
data class ThrowsKDoc(
    val name: String,
    val comment: CommentKDoc
)

/**
 * Represents a @see tag in KDoc
 */
@Serializable
data class SeeAlsoKDoc(
    val link: String
)

/**
 * Represents other KDoc tags like @author, @since, etc.
 */
@Serializable
data class OtherKDoc(
    val name: String,
    val comment: CommentKDoc
) 