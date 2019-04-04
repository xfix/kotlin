// IGNORE_BACKEND: JVM_IR
// WITH_RUNTIME
// WITH_COROUTINES
// CHECK_NEW_COUNT: function=suspendHere count=0
// COMMON_COROUTINES_TEST
// CHECK_NO_CONTINUATION: suspendHere$1
import helpers.*
import COROUTINES_PACKAGE.*
import COROUTINES_PACKAGE.intrinsics.*

suspend fun suspendThere(v: String): String = suspendCoroutineUninterceptedOrReturn { x ->
    x.resume(v)
    COROUTINE_SUSPENDED
}

suspend fun suspendHere(): String = suspendThere("OK")

fun builder(c: suspend () -> Unit) {
    c.startCoroutine(EmptyContinuation)
}

fun box(): String {
    var result = ""

    builder {
        result = suspendHere()
    }

    return result
}
