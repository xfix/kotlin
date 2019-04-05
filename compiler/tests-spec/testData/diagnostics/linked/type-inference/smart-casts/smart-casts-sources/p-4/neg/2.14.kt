// !LANGUAGE: +NewInference
// !DIAGNOSTICS: -UNUSED_EXPRESSION
// SKIP_TXT

/*
 * KOTLIN DIAGNOSTICS SPEC TEST (NEGATIVE)
 *
 * SPEC VERSION: 0.1-draft
 * PLACE: type-inference, smart-casts, smart-casts-sources -> paragraph 4 -> sentence 2
 * NUMBER: 14
 * DESCRIPTION: Smartcasts from nullability condition (value or reference equality) using if expression and simple types.
 * HELPERS: classes, objects, typealiases, functions, enumClasses, interfaces, sealedClasses
 */

// TESTCASE NUMBER: 1
fun test1(x: ClassLevel1?) {
    if (x!! is ClassLevel2) {
        <!DEBUG_INFO_EXPRESSION_TYPE("ClassLevel1 & ClassLevel1?")!>x<!>
        <!DEBUG_INFO_EXPRESSION_TYPE("ClassLevel1 & ClassLevel1?")!>x<!>.<!UNRESOLVED_REFERENCE!>test2<!>()
    }
}

// TESTCASE NUMBER: 2
fun case_2(x: Any?) {
    (x as ClassLevel1?)!!
    <!DEBUG_INFO_EXPRESSION_TYPE("ClassLevel1? & kotlin.Any?")!>x<!>
    <!DEBUG_INFO_EXPRESSION_TYPE("ClassLevel1? & kotlin.Any?")!>x<!><!UNSAFE_CALL!>.<!>test1()
}

// TESTCASE NUMBER: 3
fun case_3(x: Any?) {
    if (x as ClassLevel1? is ClassLevel1) {
        <!DEBUG_INFO_EXPRESSION_TYPE("ClassLevel1? & kotlin.Any?")!>x<!>
        <!DEBUG_INFO_EXPRESSION_TYPE("ClassLevel1? & kotlin.Any?")!>x<!><!UNSAFE_CALL!>.<!>test1()
    }
}

// TESTCASE NUMBER: 4
fun case_4(x: Any?) {
    if ((x as Class).prop_8 != null) {
        <!DEBUG_INFO_EXPRESSION_TYPE("Class & kotlin.Any & kotlin.Any?")!>x<!>
        <!DEBUG_INFO_EXPRESSION_TYPE("Class & kotlin.Any?"), DEBUG_INFO_SMARTCAST!>x<!>.prop_8<!UNSAFE_CALL!>.<!>prop_8
    }
}

// TESTCASE NUMBER: 5
fun case_5(x: Class) {
    if (x<!UNNECESSARY_NOT_NULL_ASSERTION!>!!<!>.prop_8 != null) {
        <!DEBUG_INFO_EXPRESSION_TYPE("Class")!>x<!>
        <!DEBUG_INFO_EXPRESSION_TYPE("Class")!>x<!>.prop_8<!UNSAFE_CALL!>.<!>prop_8
    }
}
