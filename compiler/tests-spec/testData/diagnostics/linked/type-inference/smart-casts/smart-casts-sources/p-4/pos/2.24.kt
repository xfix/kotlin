// !LANGUAGE: +NewInference
// !DIAGNOSTICS: -UNUSED_EXPRESSION
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
 * ISSUES: KT-28362
 */
fun case_1(x: Any) {
    if (x is Interface1) {
        if (x is Interface2) {
            <!DEBUG_INFO_EXPRESSION_TYPE("Interface1 & Interface2 & kotlin.Any")!>x<!>
            <!DEBUG_INFO_EXPRESSION_TYPE("Interface2 & kotlin.Any"), DEBUG_INFO_SMARTCAST!>x<!>.itest()
            <!DEBUG_INFO_EXPRESSION_TYPE("Interface1 & kotlin.Any"), DEBUG_INFO_SMARTCAST!>x<!>.itest1()
            <!DEBUG_INFO_EXPRESSION_TYPE("Interface2 & kotlin.Any"), DEBUG_INFO_SMARTCAST!>x<!>.itest2()
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
            <!DEBUG_INFO_EXPRESSION_TYPE("Interface1 & kotlin.Any"), DEBUG_INFO_SMARTCAST!>x<!>.itest0()
        }
    }
}

/*
 * TESTCASE NUMBER: 3
 * ISSUES: KT-28362
 */
fun case_3(x: Any) {
    if (x is Interface1) {
        if (x is Interface2) {
            <!DEBUG_INFO_EXPRESSION_TYPE("Interface1 & Interface2 & kotlin.Any")!>x<!>
            <!DEBUG_INFO_EXPRESSION_TYPE("Interface2 & kotlin.Any"), DEBUG_INFO_SMARTCAST!>x<!>.itest000()
        }
    }
}

/*
 * TESTCASE NUMBER: 4
 * ISSUES: KT-28362
 */
fun case_4(x: Any) {
    if (x is Interface2) {
        if (x is Interface1) {
            <!DEBUG_INFO_EXPRESSION_TYPE("Interface1 & Interface2 & kotlin.Any")!>x<!>
            <!DEBUG_INFO_EXPRESSION_TYPE("Interface2 & kotlin.Any"), DEBUG_INFO_SMARTCAST!>x<!>.itest0000()
        }
    }
}
