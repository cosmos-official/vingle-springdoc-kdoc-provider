package dev.vingle.kdoc.processor

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import dev.vingle.kdoc.model.ClassKDoc
import dev.vingle.kdoc.model.CommentKDoc
import dev.vingle.kdoc.model.MethodKDoc
import dev.vingle.kdoc.model.OtherKDoc
import dev.vingle.kdoc.model.ParamKDoc
import dev.vingle.kdoc.model.SeeAlsoKDoc
import dev.vingle.kdoc.model.ThrowsKDoc
import java.security.MessageDigest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * KSP processor that extracts KDoc comments and generates JSON files for runtime access
 */
class KDocProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
    private val options: Map<String, String>
) : SymbolProcessor {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    private val processedPackages = options["kdoc.packages"]?.split(",")?.toSet()
    private val processedClasses = mutableSetOf<String>()
    private val disableCache = options["kdoc.disable-cache"]?.toBoolean() ?: false
    private val forceRegenerate = options["kdoc.force-regenerate"]?.toBoolean() ?: false
    private val classContentHashes = mutableMapOf<String, String>()

    override fun process(resolver: Resolver): List<KSAnnotated> {
        // Clear processing state when cache is disabled or force regenerate is enabled
        if (disableCache || forceRegenerate) {
            processedClasses.clear()
            classContentHashes.clear()
            logger.info("Cache disabled or force regenerate enabled - processing all classes")
        }

        val symbols = resolver.getSymbolsWithAnnotation("org.springframework.web.bind.annotation.RestController")
            .filterIsInstance<KSClassDeclaration>()
            .filter { shouldProcessClass(it) }

        symbols.forEach { classSymbol ->
            processClass(classSymbol)
        }

        return emptyList()
    }

    private fun shouldProcessClass(classDeclaration: KSClassDeclaration): Boolean {
        val packageName = classDeclaration.packageName.asString()
        return processedPackages?.any { packageName.startsWith(it) } ?: true
    }

    private fun processClass(classDeclaration: KSClassDeclaration) {
        val className = classDeclaration.qualifiedName?.asString() ?: return

        // Calculate content hash to determine if regeneration is needed
        val contentHash = calculateClassContentHash(classDeclaration)
        val previousHash = classContentHashes[className]

        // Skip if already processed and content hasn't changed (unless cache is disabled)
        if (!disableCache && !forceRegenerate &&
            processedClasses.contains(className) &&
            previousHash == contentHash
        ) {
            logger.info("Skipping $className - content unchanged")
            return
        }

        logger.info("Processing KDoc for class: $className")
        processedClasses.add(className)
        classContentHashes[className] = contentHash

        val methods = mutableListOf<MethodKDoc>()
        val constructors = mutableListOf<MethodKDoc>()

        // Process functions
        classDeclaration.getAllFunctions().forEach { function ->
            val methodKDoc = processFunction(function)
            if (methodKDoc != null) {
                methods.add(methodKDoc)
            }
        }

        // Process constructors
        classDeclaration.primaryConstructor?.let { constructor ->
            val constructorKDoc = processFunction(constructor, isConstructor = true)
            if (constructorKDoc != null) {
                constructors.add(constructorKDoc)
            }
        }

        val parsedClassKDoc = parseKDocComment(classDeclaration.docString)
        val classKDoc = ClassKDoc(
            name = className,
            comment = parsedClassKDoc.mainComment,
            methods = methods,
            constructors = constructors,
            seeAlso = parsedClassKDoc.seeAlso,
            other = parsedClassKDoc.other
        )

        writeKDocToFile(className, classKDoc)
    }

    private fun processFunction(
        function: KSFunctionDeclaration,
        isConstructor: Boolean = false
    ): MethodKDoc? {
        val functionName = function.simpleName.asString()
        val paramTypes = function.parameters.map {
            it.type.resolve().declaration.simpleName.asString()
        }

        val parsedKDoc = parseKDocComment(function.docString)

        return MethodKDoc(
            name = functionName,
            paramTypes = paramTypes,
            comment = parsedKDoc.mainComment,
            params = parsedKDoc.params,
            returns = parsedKDoc.returns,
            throws = parsedKDoc.throws,
            seeAlso = parsedKDoc.seeAlso,
            other = parsedKDoc.other,
            isConstructor = isConstructor
        )
    }

    /**
     * Calculate a hash of the class content to determine if regeneration is needed
     */
    private fun calculateClassContentHash(classDeclaration: KSClassDeclaration): String {
        val content = StringBuilder()

        // Include class name and package
        content.append(classDeclaration.qualifiedName?.asString() ?: "")

        // Include class KDoc
        content.append(classDeclaration.docString ?: "")

        // Include function signatures and KDoc
        classDeclaration.getAllFunctions().forEach { function ->
            content.append(function.simpleName.asString())
            content.append(function.parameters.joinToString(",") {
                it.type.resolve().declaration.simpleName.asString()
            })
            content.append(function.docString ?: "")
        }

        // Include constructor
        classDeclaration.primaryConstructor?.let { constructor ->
            content.append("constructor")
            content.append(constructor.parameters.joinToString(",") {
                it.type.resolve().declaration.simpleName.asString()
            })
            content.append(constructor.docString ?: "")
        }

        return MessageDigest.getInstance("MD5")
            .digest(content.toString().toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    private data class ParsedKDoc(
        val mainComment: CommentKDoc,
        val params: List<ParamKDoc> = emptyList(),
        val returns: CommentKDoc = CommentKDoc.empty(),
        val throws: List<ThrowsKDoc> = emptyList(),
        val seeAlso: List<SeeAlsoKDoc> = emptyList(),
        val other: List<OtherKDoc> = emptyList()
    )

    private fun parseKDocComment(docString: String?): ParsedKDoc {
        if (docString.isNullOrBlank()) {
            return ParsedKDoc(CommentKDoc.empty())
        }

        val lines = docString.lines().map { it.trimStart('*', ' ', '\t') }
        val mainCommentLines = mutableListOf<String>()
        val params = mutableListOf<ParamKDoc>()
        val throws = mutableListOf<ThrowsKDoc>()
        val seeAlso = mutableListOf<SeeAlsoKDoc>()
        val other = mutableListOf<OtherKDoc>()
        var returns = CommentKDoc.empty()

        var currentSection: String? = null
        var currentContent = mutableListOf<String>()

        for (line in lines) {
            when {
                line.startsWith("@param ") -> {
                    finishCurrentSection(
                        currentSection,
                        currentContent,
                        mainCommentLines,
                        params,
                        throws,
                        seeAlso,
                        other
                    ) { returns = it }
                    currentSection = "param"
                    currentContent.clear()
                    currentContent.add(line.removePrefix("@param "))
                }

                line.startsWith("@return ") -> {
                    finishCurrentSection(
                        currentSection,
                        currentContent,
                        mainCommentLines,
                        params,
                        throws,
                        seeAlso,
                        other
                    ) { returns = it }
                    currentSection = "return"
                    currentContent.clear()
                    currentContent.add(line.removePrefix("@return "))
                }

                line.startsWith("@throws ") -> {
                    finishCurrentSection(
                        currentSection,
                        currentContent,
                        mainCommentLines,
                        params,
                        throws,
                        seeAlso,
                        other
                    ) { returns = it }
                    currentSection = "throws"
                    currentContent.clear()
                    currentContent.add(line.removePrefix("@throws "))
                }

                line.startsWith("@see ") -> {
                    finishCurrentSection(
                        currentSection,
                        currentContent,
                        mainCommentLines,
                        params,
                        throws,
                        seeAlso,
                        other
                    ) { returns = it }
                    currentSection = "see"
                    currentContent.clear()
                    currentContent.add(line.removePrefix("@see "))
                }

                line.startsWith("@") -> {
                    finishCurrentSection(
                        currentSection,
                        currentContent,
                        mainCommentLines,
                        params,
                        throws,
                        seeAlso,
                        other
                    ) { returns = it }
                    currentSection = "other"
                    currentContent.clear()
                    currentContent.add(line)
                }

                else -> {
                    currentContent.add(line)
                }
            }
        }

        // Finish the last section
        finishCurrentSection(
            currentSection,
            currentContent,
            mainCommentLines,
            params,
            throws,
            seeAlso,
            other
        ) { returns = it }

        val mainComment = CommentKDoc(
            text = mainCommentLines.joinToString("\n").trim(),
            inlineTags = emptyList()
        )

        return ParsedKDoc(mainComment, params, returns, throws, seeAlso, other)
    }

    private fun finishCurrentSection(
        section: String?,
        content: List<String>,
        mainCommentLines: MutableList<String>,
        params: MutableList<ParamKDoc>,
        throws: MutableList<ThrowsKDoc>,
        seeAlso: MutableList<SeeAlsoKDoc>,
        other: MutableList<OtherKDoc>,
        setReturns: (CommentKDoc) -> Unit
    ) {
        if (content.isEmpty()) return

        when (section) {
            null -> mainCommentLines.addAll(content)
            "param" -> {
                val firstLine = content.first()
                val parts = firstLine.split(" ", limit = 2)
                if (parts.size == 2) {
                    val paramName = parts[0]
                    val paramDesc = (listOf(parts[1]) + content.drop(1)).joinToString("\n").trim()
                    params.add(ParamKDoc(paramName, CommentKDoc(paramDesc)))
                }
            }

            "return" -> {
                val returnDesc = content.joinToString("\n").trim()
                setReturns(CommentKDoc(returnDesc))
            }

            "throws" -> {
                val firstLine = content.first()
                val parts = firstLine.split(" ", limit = 2)
                if (parts.size == 2) {
                    val exceptionName = parts[0]
                    val throwsDesc = (listOf(parts[1]) + content.drop(1)).joinToString("\n").trim()
                    throws.add(ThrowsKDoc(exceptionName, CommentKDoc(throwsDesc)))
                }
            }

            "see" -> {
                val link = content.joinToString("\n").trim()
                seeAlso.add(SeeAlsoKDoc(link))
            }

            "other" -> {
                val firstLine = content.first()
                if (firstLine.startsWith("@")) {
                    val tagName = firstLine.substringBefore(" ").removePrefix("@")
                    val tagContent = firstLine.substringAfter(" ", "") + content.drop(1).joinToString("\n")
                    other.add(OtherKDoc(tagName, CommentKDoc(tagContent.trim())))
                }
            }
        }
    }

    private fun writeKDocToFile(className: String, classKDoc: ClassKDoc) {
        val resourcePath = "kdoc/${className.replace('.', '/')}.json"

        try {
            val file = codeGenerator.createNewFile(
                dependencies = Dependencies(false),
                packageName = "",
                fileName = resourcePath,
                extensionName = ""
            )

            file.use { outputStream ->
                val jsonString = json.encodeToString(classKDoc)
                outputStream.write(jsonString.toByteArray())
            }

            logger.info("Generated KDoc file: $resourcePath")
        } catch (e: Exception) {
            logger.error("Failed to write KDoc file for $className: ${e.message}")
        }
    }
}

/**
 * KSP processor provider
 */
class KDocProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return KDocProcessor(
            codeGenerator = environment.codeGenerator,
            logger = environment.logger,
            options = environment.options
        )
    }
} 
