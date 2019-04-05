// !LANGUAGE: +NewInference
// !DIAGNOSTICS: -UNUSED_EXPRESSION
// SKIP_TXT

/*
 * KOTLIN DIAGNOSTICS SPEC TEST (NEGATIVE)
 *
 * SPEC VERSION: 0.1-draft
 * PLACE: type-inference, smart-casts, smart-casts-sources -> paragraph 4 -> sentence 2
 * NUMBER: 19
 * DESCRIPTION: Smartcasts from nullability condition (value or reference equality) using if expression and simple types.
 * HELPERS: classes, objects, typealiases, functions, enumClasses, interfaces, sealedClasses
 */

/*
 * TESTCASE NUMBER: 1
 * UNEXPECTED BEHAVIOUR
 * ISSUES: KT-30826
 */
fun case_1(x: Interface1?) {
    var y = x
    y as Interface2
    val foo = {
        <!DEBUG_INFO_EXPRESSION_TYPE("Interface1?")!>y<!>.itest2()
    }
    y = null
    foo()
}

/*
 * TESTCASE NUMBER: 2
 * UNEXPECTED BEHAVIOUR
 * ISSUES: KT-30826
 */
fun case_2(x: Interface1?) {
    var y = x
    y as Interface2
    val foo = fun () {
        <!DEBUG_INFO_EXPRESSION_TYPE("Interface1?")!>y<!>.itest2()
    }
    y = null
    foo()
}

/*
 * TESTCASE NUMBER: 3
 * UNEXPECTED BEHAVIOUR
 * ISSUES: KT-30826
 */
fun case_3(x: Interface1?) {
    var y = x
    y as Interface2
    fun foo() {
        <!DEBUG_INFO_EXPRESSION_TYPE("Interface1?")!>y<!>.itest2()
    }
    y = null
    foo()
}

// TESTCASE NUMBER: 4
fun case_4(x: Interface1?) {
    var y = x
    y as Interface2
    y = null
    fun foo() {
        <!DEBUG_INFO_EXPRESSION_TYPE("Interface1?")!>y<!>.<!UNRESOLVED_REFERENCE!>itest2<!>()
    }
    y = x
    foo()
}

// TESTCASE NUMBER: 5
fun case_5(x: Interface1?) {
    var y = x
    y as Interface2
    y = null
    fun foo() {
        <!DEBUG_INFO_EXPRESSION_TYPE("Interface1?")!>y<!>.<!UNRESOLVED_REFERENCE!>itest2<!>()
    }
    y = x
    foo()
}
