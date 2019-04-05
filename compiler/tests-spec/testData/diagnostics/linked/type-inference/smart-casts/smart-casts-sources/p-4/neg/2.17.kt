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
fun case_1(x: ClassWithCustomEquals) {
    val y = null
    if (x == <!DEBUG_INFO_CONSTANT!>y<!>) {
        <!DEBUG_INFO_EXPRESSION_TYPE("ClassWithCustomEquals & kotlin.Nothing")!>x<!>
        <!DEBUG_INFO_EXPRESSION_TYPE("ClassWithCustomEquals & kotlin.Nothing"), DEBUG_INFO_SMARTCAST!>x<!>.<!UNREACHABLE_CODE!><!MISSING_DEPENDENCY_CLASS, MISSING_DEPENDENCY_CLASS!>inv<!>()<!>
    }
}

// TESTCASE NUMBER: 2
fun case_2(x: ClassWithCustomEquals) {
    if (<!SENSELESS_COMPARISON!>x == null<!>) {
        <!DEBUG_INFO_EXPRESSION_TYPE("ClassWithCustomEquals & kotlin.Nothing")!>x<!>
        <!DEBUG_INFO_EXPRESSION_TYPE("ClassWithCustomEquals & kotlin.Nothing"), DEBUG_INFO_SMARTCAST!>x<!>.<!UNREACHABLE_CODE!><!MISSING_DEPENDENCY_CLASS, MISSING_DEPENDENCY_CLASS!>inv<!>()<!>
    }
}

/*
 * TESTCASE NUMBER: 3
 * UNEXPECTED BEHAVIOUR
 * ISSUES: KT-28243
 */
fun case_3(x: ClassWithCustomEquals, y: Nothing?) {
    if (x == <!DEBUG_INFO_CONSTANT!>y<!>) {
        <!DEBUG_INFO_EXPRESSION_TYPE("ClassWithCustomEquals & kotlin.Nothing")!>x<!>
        <!DEBUG_INFO_EXPRESSION_TYPE("ClassWithCustomEquals & kotlin.Nothing"), DEBUG_INFO_SMARTCAST!>x<!>.<!UNREACHABLE_CODE!><!MISSING_DEPENDENCY_CLASS, MISSING_DEPENDENCY_CLASS!>inv<!>()<!>
    }
}

/*
 * TESTCASE NUMBER: 4
 * UNEXPECTED BEHAVIOUR
 * ISSUES: KT-28265
 */
fun case_4(x: ClassWithCustomEquals) {
    val y = null
    if (<!DEBUG_INFO_CONSTANT!>y<!> == x) {
        <!DEBUG_INFO_EXPRESSION_TYPE("ClassWithCustomEquals & kotlin.Nothing")!>x<!>
        <!DEBUG_INFO_EXPRESSION_TYPE("ClassWithCustomEquals & kotlin.Nothing"), DEBUG_INFO_SMARTCAST!>x<!>.<!UNREACHABLE_CODE!><!MISSING_DEPENDENCY_CLASS, MISSING_DEPENDENCY_CLASS!>inv<!>()<!>
    }
}

/*
 * TESTCASE NUMBER: 5
 * UNEXPECTED BEHAVIOUR
 * ISSUES: KT-28243
 */
fun case_5(x: ClassWithCustomEquals, y: Nothing?) {
    if (<!DEBUG_INFO_CONSTANT!>y<!> == x) {
        <!DEBUG_INFO_EXPRESSION_TYPE("ClassWithCustomEquals & kotlin.Nothing")!>x<!>
        <!DEBUG_INFO_EXPRESSION_TYPE("ClassWithCustomEquals & kotlin.Nothing"), DEBUG_INFO_SMARTCAST!>x<!>.<!UNREACHABLE_CODE!><!MISSING_DEPENDENCY_CLASS, MISSING_DEPENDENCY_CLASS!>inv<!>()<!>
    }
}

/*
 * TESTCASE NUMBER: 6
 * UNEXPECTED BEHAVIOUR
 * ISSUES: KT-28265
 */
fun case_6(x: ClassWithCustomEquals) {
    val y = null
    if (x == <!DEBUG_INFO_CONSTANT!>y<!> == true) {
        <!DEBUG_INFO_EXPRESSION_TYPE("ClassWithCustomEquals & kotlin.Nothing")!>x<!>
        <!DEBUG_INFO_EXPRESSION_TYPE("ClassWithCustomEquals & kotlin.Nothing"), DEBUG_INFO_SMARTCAST!>x<!>.<!UNREACHABLE_CODE!><!MISSING_DEPENDENCY_CLASS, MISSING_DEPENDENCY_CLASS!>inv<!>()<!>
    }
}

// TESTCASE NUMBER: 7
fun case_7(x: ClassWithCustomEquals) {
    if ((<!SENSELESS_COMPARISON!>x != null<!>) == false) {
        <!DEBUG_INFO_EXPRESSION_TYPE("ClassWithCustomEquals & kotlin.Nothing")!>x<!>
        <!DEBUG_INFO_EXPRESSION_TYPE("ClassWithCustomEquals & kotlin.Nothing"), DEBUG_INFO_SMARTCAST!>x<!>.<!UNREACHABLE_CODE!><!MISSING_DEPENDENCY_CLASS, MISSING_DEPENDENCY_CLASS!>inv<!>()<!>
    }
}

/*
 * TESTCASE NUMBER: 8
 * UNEXPECTED BEHAVIOUR
 * ISSUES: KT-28243
 */
fun case_8(x: ClassWithCustomEquals, y: Nothing?) {
    if (!(<!DEBUG_INFO_CONSTANT!>y<!> == x) == false) {
        <!DEBUG_INFO_EXPRESSION_TYPE("ClassWithCustomEquals & kotlin.Nothing")!>x<!>
        <!DEBUG_INFO_EXPRESSION_TYPE("ClassWithCustomEquals & kotlin.Nothing"), DEBUG_INFO_SMARTCAST!>x<!>.<!UNREACHABLE_CODE!><!MISSING_DEPENDENCY_CLASS, MISSING_DEPENDENCY_CLASS!>inv<!>()<!>
    }
}

/*
 * TESTCASE NUMBER: 9
 * UNEXPECTED BEHAVIOUR
 * ISSUES: KT-28243
 */
fun case_9(x: ClassWithCustomEquals?, y: Interface1) {
    if (x == y) {
        <!DEBUG_INFO_EXPRESSION_TYPE("ClassWithCustomEquals & ClassWithCustomEquals?")!>x<!>
        <!DEBUG_INFO_EXPRESSION_TYPE("ClassWithCustomEquals & ClassWithCustomEquals?")!>x<!>.<!UNRESOLVED_REFERENCE!>itest1<!>()
    }
}