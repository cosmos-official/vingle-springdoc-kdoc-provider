package dev.vingle.kdoc

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNull
import org.springdoc.core.providers.SpringDocJavadocProvider

/** This test class ensures that the compatibility layer code works with SpringDoc. */
internal class SpringDocCompatibility {
    /**
     * This tests the fallback branches in [SpringDocJavadocProvider], which usually call a static function for the
     * given target object in `TherApiModels` to create an empty instance.
     */
    @Test
    fun runEmpty() {
        val exampleMethod = ExampleClass::class.java.getMethod(ExampleClass::exampleMethod.name, String::class.java)
        val exampleField = ExampleClass::class.java.getField(ExampleClass::exampleField.name)
        val springDocProvider = SpringDocJavadocProvider()

        assertEquals("", springDocProvider.getClassJavadoc(ExampleClass::class.java))
        assertEquals("", springDocProvider.getFieldJavadoc(exampleField))
        assertNull(springDocProvider.getParamJavadoc(exampleMethod, "exampleParam"))
        assertEquals("", springDocProvider.getMethodJavadocReturn(exampleMethod))
        assertEquals(emptyMap<Nothing, Nothing>(), springDocProvider.getMethodJavadocThrows(exampleMethod))
        assertEquals("", springDocProvider.getMethodJavadocDescription(exampleMethod))
        assertEquals(
            emptyMap<Nothing, Nothing>(),
            springDocProvider.getRecordClassParamJavadoc(ExampleRecord::class.java),
        )
    }
}

internal class ExampleClass {
    @JvmField
    val exampleField = "value"

    fun exampleMethod(@Suppress("unused") exampleParam: String) = Unit
}

@JvmRecord
internal data class ExampleRecord(val value: String)