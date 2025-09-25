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
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSNode
import com.google.devtools.ksp.symbol.KSValueParameter
import dev.vingle.kdoc.model.ClassKDoc
import dev.vingle.kdoc.model.CommentKDoc
import dev.vingle.kdoc.model.MethodKDoc
import dev.vingle.kdoc.model.OtherKDoc
import dev.vingle.kdoc.model.ParamKDoc
import dev.vingle.kdoc.model.SeeAlsoKDoc
import dev.vingle.kdoc.model.ThrowsKDoc
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

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
    private val disableCache = options["kdoc.disable-cache"]?.toBoolean() ?: false
    private val forceRegenerate = options["kdoc.force-regenerate"]?.toBoolean() ?: false
    private val debugMode = options["kdoc.debug"]?.toBoolean() ?: false

    // Thread-safe collections for concurrent access
    private val processedClasses = ConcurrentHashMap.newKeySet<String>()
    private val classContentHashes = ConcurrentHashMap<String, String>()

    override fun process(resolver: Resolver): List<KSAnnotated> {
        // Clear processing state when cache is disabled or force regenerate is enabled
        if (disableCache || forceRegenerate) {
            processedClasses.clear()
            classContentHashes.clear()
            if (debugMode) {
                logger.info("Cache disabled or force regenerate enabled - processing all classes")
            }
        }

        val processedFilesInRound = mutableSetOf<KSFile>()

        val symbols =
            resolver.getSymbolsWithAnnotation("org.springframework.web.bind.annotation.RestController")
                .filterIsInstance<KSClassDeclaration>()
                .filter { shouldProcessClass(it) }

        if (debugMode) {
            logger.info("Found ${symbols.count()} RestController classes to process")
        }

        symbols.forEach { classSymbol ->
            try {
                processClass(classSymbol)
                // Track the source file for dependency management
                classSymbol.containingFile?.let { file ->
                    processedFilesInRound.add(file)
                }
            } catch (e: Exception) {
                logger.error(
                    "Error processing class ${classSymbol.qualifiedName?.asString()}: ${e.message}",
                    classSymbol
                )
            }
        }

        if (debugMode) {
            logger.info("Processed ${processedClasses.size} classes in total, ${processedFilesInRound.size} files in this round")
        }

        return emptyList()
    }

    private fun shouldProcessClass(classDeclaration: KSClassDeclaration): Boolean {
        val packageName = classDeclaration.packageName.asString()
        return processedPackages?.any { packageName.startsWith(it) } ?: true
    }

    private fun processClass(classDeclaration: KSClassDeclaration) {
        val className = classDeclaration.qualifiedName?.asString() ?: return

        val declaredFunctions = classDeclaration.declarations
            .filterIsInstance<KSFunctionDeclaration>()
            .toList()
        val primaryConstructor = classDeclaration.primaryConstructor

        val methods = declaredFunctions.mapNotNull { processFunction(it) }
        val constructors = primaryConstructor?.let { constructor ->
            processFunction(constructor, isConstructor = true)?.let(::listOf)
        } ?: emptyList()

        val parsedClassKDoc = parseKDocComment(safeDocString(classDeclaration))
        val classKDoc = ClassKDoc(
            name = className,
            comment = parsedClassKDoc.mainComment,
            methods = methods,
            constructors = constructors,
            seeAlso = parsedClassKDoc.seeAlso,
            other = parsedClassKDoc.other
        )

        val jsonString = json.encodeToString(classKDoc)
        val contentHash = jsonString.md5()
        val previousHash = classContentHashes[className]

        val shouldSkip = !disableCache && !forceRegenerate && previousHash != null &&
                previousHash == contentHash && processedClasses.contains(className)

        if (shouldSkip) {
            if (debugMode) {
                logger.info("Skipping $className - content unchanged (hash: $contentHash)")
            }
            return
        }

        if (debugMode) {
            logger.info("Processing KDoc for class: $className (hash: $contentHash, prev: $previousHash)")
        }

        processedClasses.add(className)
        classContentHashes[className] = contentHash

        val sourceFiles = buildSet<KSFile> {
            classDeclaration.containingFile?.let(::add)
            declaredFunctions.mapNotNull { it.containingFile }.forEach(::add)
            primaryConstructor?.containingFile?.let(::add)
        }

        writeKDocToFile(className, jsonString, sourceFiles)
    }

    private fun processFunction(
        function: KSFunctionDeclaration,
        isConstructor: Boolean = false
    ): MethodKDoc? {
        val functionName = function.simpleName.asString()

        return try {
            val paramTypes = safeParameterTypes(function)

            val parsedKDoc = try {
                parseKDocComment(safeDocString(function))
            } catch (e: Exception) {
                logger.warn("Failed to parse KDoc for function $functionName: ${e.message}")
                ParsedKDoc(CommentKDoc.empty())
            }

            MethodKDoc(
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
        } catch (e: Exception) {
            val message = "Falling back to empty documentation for $functionName: ${e.message}"
            if (debugMode) {
                logger.warn(message, function)
            } else {
                logger.warn(message)
            }
            MethodKDoc(
                name = functionName,
                paramTypes = emptyList(),
                comment = CommentKDoc.empty(),
                params = emptyList(),
                returns = CommentKDoc.empty(),
                throws = emptyList(),
                seeAlso = emptyList(),
                other = emptyList(),
                isConstructor = isConstructor
            )
        }
    }

    private fun safeParameterTypes(function: KSFunctionDeclaration): List<String> {
        return safePsiRead(function, "parameter types for ${function.simpleName.asString()}") {
            function.parameters.map { param -> resolveParameterType(param) }
        } ?: emptyList()
    }

    private fun resolveParameterType(param: KSValueParameter): String {
        val resolvedName = safePsiRead(
            param,
            "resolved type for parameter ${param.name?.asString() ?: "<anonymous>"}"
        ) {
            param.type.resolve().declaration.simpleName.asString()
        }
        if (!resolvedName.isNullOrBlank()) {
            return resolvedName
        }

        val fallbackName = safePsiRead(
            param,
            "string representation for parameter ${param.name?.asString() ?: "<anonymous>"}"
        ) {
            val typeString = param.type.toString()
            typeString.substringAfterLast('.').substringBefore('?').substringBefore('<')
        }

        return fallbackName?.takeIf { it.isNotBlank() } ?: "Unknown"
    }

    private fun safeDocString(node: KSDeclaration?): String? {
        if (node == null) {
            return null
        }

        val context = when (node) {
            is KSClassDeclaration -> "docString for ${node.qualifiedName?.asString() ?: node.simpleName.asString()}"
            is KSFunctionDeclaration -> "docString for ${node.simpleName.asString()}"
            else -> "docString"
        }

        return safePsiRead(node, context) { node.docString }
    }

    private inline fun <T> safePsiRead(
        node: KSNode?,
        context: String,
        block: () -> T
    ): T? {
        return try {
            block()
        } catch (e: IllegalStateException) {
            val message = "Skipping $context because PSI became invalid: ${e.message}"
            if (debugMode) {
                if (node != null) {
                    logger.warn(message, node)
                } else {
                    logger.warn(message)
                }
            } else {
                logger.info(message)
            }
            null
        }
    }

    private fun String.md5(): String = MessageDigest.getInstance("MD5")
        .digest(toByteArray())
        .joinToString("") { "%02x".format(it) }

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
                    val tagContent =
                        firstLine.substringAfter(" ", "") + content.drop(1).joinToString("\n")
                    other.add(OtherKDoc(tagName, CommentKDoc(tagContent.trim())))
                }
            }
        }
    }

    private fun writeKDocToFile(
        className: String,
        jsonString: String,
        sourceFiles: Set<KSFile> = emptySet()
    ) {
        val resourcePath = "kdoc/${className.replace('.', '/')}.json"

        try {
            // Create proper dependencies from source files to ensure incremental compilation works correctly
            val dependencies = if (sourceFiles.isNotEmpty()) {
                Dependencies(true, *sourceFiles.toTypedArray())
            } else {
                Dependencies(false)
            }

            val file = codeGenerator.createNewFile(
                dependencies = dependencies,
                packageName = "",
                fileName = resourcePath,
                extensionName = ""
            )

            file.use { outputStream ->
                outputStream.write(jsonString.toByteArray())
            }

            if (debugMode) {
                logger.info("Generated KDoc file: $resourcePath with ${sourceFiles.size} dependencies")
            }
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
