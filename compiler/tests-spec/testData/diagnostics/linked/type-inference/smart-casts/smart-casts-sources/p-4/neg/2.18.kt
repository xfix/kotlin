// !LANGUAGE: +NewInference
// !DIAGNOSTICS: -UNUSED_EXPRESSION
// SKIP_TXT

/*
 * KOTLIN DIAGNOSTICS SPEC TEST (NEGATIVE)
 *
 * SPEC VERSION: 0.1-draft
 * PLACE: type-inference, smart-casts, smart-casts-sources -> paragraph 4 -> sentence 2
 * NUMBER: 18
 * DESCRIPTION: Smartcasts from nullability condition (value or reference equality) using if expression and simple types.
 * HELPERS: classes, objects, typealiases, functions, enumClasses, interfaces, sealedClasses
 */

/*
 * TESTCASE NUMBER: 1
 * ISSUES: KT-28362
 */
fun case_1(x: Any) {
    if (x is Interface1) {
        if (x is Interface2) {
            <!DEBUG_INFO_EXPRESSION_TYPE("Interface1 & Interface2 & kotlin.Any")!>x<!>
            <!DEBUG_INFO_EXPRESSION_TYPE("Interface1 & Interface2 & kotlin.Any")!>x<!>.<!OVERLOAD_RESOLUTION_AMBIGUITY!>itest00<!>()
        }
    }
}

/*
 * TESTCASE NUMBER: 2
 * ISSUES: KT-28362
 */
fun case_2(x: Any) {
    if (x is Interface2) {
        if (x is Interface1) {
            <!DEBUG_INFO_EXPRESSION_TYPE("Interface1 & Interface2 & kotlin.Any")!>x<!>
            <!DEBUG_INFO_EXPRESSION_TYPE("Interface1 & Interface2 & kotlin.Any")!>x<!>.<!OVERLOAD_RESOLUTION_AMBIGUITY!>itest00000<!>()
        }
    }
}
