package it.hydr4.argo.annotations

/**
 * Marks surface that is stable in shape but may still evolve in a future
 * minor release. Code depending on it should be easy to update.
 */
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.CONSTRUCTOR,
    AnnotationTarget.TYPEALIAS,
)
@Retention(AnnotationRetention.BINARY)
public annotation class Beta(public val note: String = "")

/**
 * Marks models and endpoints whose wire shape is the least stable upstream:
 * fields may appear, disappear or change type without a library release.
 *
 * Prefer tolerant handling (nullability, lenient parsers) over depending on
 * exact shapes when consuming these.
 */
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
)
@Retention(AnnotationRetention.BINARY)
public annotation class Experimental(public val note: String = "")
