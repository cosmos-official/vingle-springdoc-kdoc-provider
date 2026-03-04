package dev.vingle.kdoc

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class RuntimeKDocTest {
    @BeforeEach
    fun resetCache(): Unit = RuntimeKDoc.clearCache()

    @Test
    fun clearCache() {
        assertEquals(0, RuntimeKDoc.currentCacheSize)
        val classKDoc = RuntimeKDoc.getKDoc(ExampleClassWithDocs::class)
        assertFalse(classKDoc.isEmpty())
        assertEquals(1, RuntimeKDoc.currentCacheSize)
        RuntimeKDoc.clearCache()
        assertEquals(0, RuntimeKDoc.currentCacheSize)
    }

    @Test
    fun getClassKDocNoFileAvailable() {
        var classKDoc = RuntimeKDoc.getKDoc(TestClassWithoutFile::class)
        assertTrue(classKDoc.isEmpty())
        classKDoc = RuntimeKDoc.getKDoc(CorruptedJson::class)
        assertTrue(classKDoc.isEmpty())
    }

    @Test
    fun getMethodKDocNoFile() {
        val targetMethod = TestClassWithoutFile::class.java.getDeclaredMethod(
            "testMethod", Int::class.java, Byte::class.java,
        )
        val methodKDoc = RuntimeKDoc.getKDoc(targetMethod)

        assertEquals(targetMethod.name, methodKDoc.name)
        assertTrue(methodKDoc.comment.isEmpty())
        assertIterableEquals(emptySet<Nothing>(), methodKDoc.params)
        assertIterableEquals(listOf("int", "byte"), methodKDoc.paramTypes)
        assertIterableEquals(emptySet<Nothing>(), methodKDoc.other)
        assertIterableEquals(emptySet<Nothing>(), methodKDoc.seeAlso)
        assertIterableEquals(emptySet<Nothing>(), methodKDoc.throws)
    }

    @Test
    fun getMethodKDoc() {
        val targetMethod = ExampleClassWithDocs::class.java.getDeclaredMethod("methodWithoutParameters")
        val methodKDoc = RuntimeKDoc.getKDoc(targetMethod)

        assertFalse(methodKDoc.isConstructor)
        assertIterableEquals(emptySet<Nothing>(), methodKDoc.params)
        assertIterableEquals(emptySet<Nothing>(), methodKDoc.paramTypes)
        val throws = methodKDoc.throws.single()
        assertEquals("NotImplementedError", throws.name)
        assertTrue(throws.comment.text.startsWith("This method always"))
        assertEquals(0, throws.comment.inlineTags.size)
        assertEquals("methodWithParameters", methodKDoc.seeAlso.single().link)
        assertEquals(0, methodKDoc.returns.inlineTags.size)
        assertTrue(methodKDoc.returns.text.startsWith("Does not return"))
    }
}

/** This class does not have a corresponding JSON file on the classpath.  */
private class TestClassWithoutFile {
    @Suppress("unused")
    fun testMethod(a: Int, b: Byte) = Unit
}

/** This class has a JSON file, but it cannot be deserialized to a class instance. */
private class CorruptedJson
