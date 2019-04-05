// !LANGUAGE: +NewInference
// !DIAGNOSTICS: -UNUSED_EXPRESSION
// SKIP_TXT

/*
 * KOTLIN DIAGNOSTICS SPEC TEST (POSITIVE)
 *
 * SPEC VERSION: 0.1-draft
 * PLACE: type-inference, smart-casts, smart-casts-sources -> paragraph 4 -> sentence 2
 * NUMBER: 22
 * DESCRIPTION: Smartcasts from nullability condition (value or reference equality) using if expression and simple types.
 * HELPERS: classes, objects, typealiases, functions, enumClasses, interfaces, sealedClasses
 */

/*
 * TESTCASE NUMBER: 1
 * UNEXPECTED BEHAVIOUR
 * ISSUES: KT-28265
 */
fun case_1(x: Number?) {
    val y: Int? = null

    if (x == y) {
        <!DEBUG_INFO_EXPRESSION_TYPE("kotlin.Number?")!>x<!>
        <!DEBUG_INFO_EXPRESSION_TYPE("kotlin.Number?")!>x<!>.<!DEBUG_INFO_UNRESOLVED_WITH_TARGET, UNRESOLVED_REFERENCE_WRONG_RECEIVER!>inv<!>()
    }
}

// TESTCASE NUMBER: 2
fun case_2(x: Number) {
    val y: Int? = null

    if (x === y) {
        <!DEBUG_INFO_EXPRESSION_TYPE("kotlin.Int & kotlin.Number")!>x<!>
        <!DEBUG_INFO_EXPRESSION_TYPE("kotlin.Int & kotlin.Number"), DEBUG_INFO_SMARTCAST!>x<!>.inv()
    }
}

// TESTCASE NUMBER: 3
fun case_3(x: Number) {
    var y: Int? = null

    if (x === y) {
        <!DEBUG_INFO_EXPRESSION_TYPE("kotlin.Int & kotlin.Number")!>x<!>
        <!DEBUG_INFO_EXPRESSION_TYPE("kotlin.Int & kotlin.Number"), DEBUG_INFO_SMARTCAST!>x<!>.inv()
    }
}

/*
 * TESTCASE NUMBER: 4
 * UNEXPECTED BEHAVIOUR
 * ISSUES: KT-28265
 */
fun case_4() {
    var x: Number? = null
    var y: Int? = null

    if (x == y) {
        <!DEBUG_INFO_EXPRESSION_TYPE("kotlin.Number?")!>x<!>
        <!DEBUG_INFO_EXPRESSION_TYPE("kotlin.Number?")!>x<!>?.<!DEBUG_INFO_UNRESOLVED_WITH_TARGET, UNRESOLVED_REFERENCE_WRONG_RECEIVER!>inv<!>()
    }
}

/*
 * TESTCASE NUMBER: 5
 * UNEXPECTED BEHAVIOUR
 * ISSUES: KT-28265
 */
fun case_5(x: Class, y: Class) {
    if (x.prop_11 == y.prop_5) {
        <!DEBUG_INFO_EXPRESSION_TYPE("kotlin.Number & kotlin.Number?")!>x.prop_11<!>
        <!DEBUG_INFO_EXPRESSION_TYPE("kotlin.Number"), DEBUG_INFO_SMARTCAST!>x.prop_11<!>.inv()
    }
}
