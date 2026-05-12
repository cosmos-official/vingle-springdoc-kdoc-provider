package dev.vingle.kdoc

/**
 * This is a data class with some documentation comments for testing.
 *
 * A JSON was generated with the processor and put into the test resources, so that it's available in the same resource
 * path as it would be in practice and can therefore be used by the tests.
 *
 * @constructor The constructor can be used to instantiate an object.
 */
data class ExampleClassWithDocs(
    /** This is an integer field. */
    val number: Int,
    /** This is a text field. */
    val text: String,
) {
    /**
     * A documented method that doesn't have any parameters.
     *
     * @throws NotImplementedError This method always raises an error.
     * @return Does not return anything because it throws an error beforehand.
     * @see methodWithParameters
     */
    fun methodWithoutParameters(): Nothing = throw NotImplementedError()

    /**
     * A documented method that *does* have parameters.
     *
     * @param textParameter A parameter that's also documented.
     * @inline
     */
    fun methodWithParameters(@Suppress("unused") textParameter: String) = Unit
}