// "Add parameter to constructor 'Foo'" "true"
// WITH_STDLIB
// DISABLE-ERRORS
// COMPILER_ARGUMENTS: -XXLanguage:-NewInference
private val foo = Foo(1, "2", <caret>setOf("3"))