// !LANGUAGE: +NewInference
// !DIAGNOSTICS: -UNUSED_EXPRESSION -UNUSED_VARIABLE -UNUSED_VALUE
// SKIP_TXT

/*
 * KOTLIN DIAGNOSTICS SPEC TEST (POSITIVE)
 *
 * SPEC VERSION: 0.1-draft
 * PLACE: type-inference, smart-casts, smart-casts-sources -> paragraph 4 -> sentence 2
 * NUMBER: 24
 * DESCRIPTION: Smartcasts from nullability condition (value or reference equality) using if expression and simple types.
 * HELPERS: classes, objects, typealiases, functions, enumClasses, interfaces, sealedClasses
 */

/*
 * TESTCASE NUMBER: 1
 * UNEXPECTED BEHAVIOUR
 * ISSUES: KT-19446
 */
fun case_1() {
    var x: Boolean? = true
    x!!
    val y = {
        val <!ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE, NAME_SHADOWING!>x<!>: Int?
        x = 10
    }
    <!DEBUG_INFO_EXPRESSION_TYPE("kotlin.Boolean?")!>x<!>
    <!DEBUG_INFO_EXPRESSION_TYPE("kotlin.Boolean?"), SMARTCAST_IMPOSSIBLE!>x<!>.equals(10)
}

/*
 * TESTCASE NUMBER: 2
 * UNEXPECTED BEHAVIOUR
 * ISSUES: KT-19446
 */
fun case_2() {
    var x: Boolean? = true
    x!!
    val y = {
        var <!ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE, NAME_SHADOWING!>x<!>: Int? = 10
        x = 10
    }
    <!DEBUG_INFO_EXPRESSION_TYPE("kotlin.Boolean?")!>x<!>
    <!DEBUG_INFO_EXPRESSION_TYPE("kotlin.Boolean?"), SMARTCAST_IMPOSSIBLE!>x<!>.equals(10)
}

// TESTCASE NUMBER: 3
fun case_3() {
    var x: Boolean? = true
    x!!
    val y = {
        var <!NAME_SHADOWING!>x<!>: Int? = 10
    }
    <!DEBUG_INFO_EXPRESSION_TYPE("kotlin.Boolean & kotlin.Boolean?")!>x<!>
    <!DEBUG_INFO_EXPRESSION_TYPE("kotlin.Boolean & kotlin.Boolean?"), DEBUG_INFO_SMARTCAST!>x<!>.equals(10)
}