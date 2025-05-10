package dev.znci.twine

import dev.znci.twine.annotations.TwineNativeFunction
import org.junit.jupiter.api.Test
import org.luaj.vm2.Globals
import org.luaj.vm2.lib.jse.JsePlatform
import kotlin.test.assertEquals

class TwineNativeTestClass: TwineNative("") {
    @TwineNativeFunction
    fun testVarargStr(vararg args: String): String {
        return args.joinToString(", ")
    }
    @TwineNativeFunction
    fun testVarargDouble(vararg args: Double): Double {
        return args.sumOf { it.toDouble() }
    }
    @TwineNativeFunction
    fun testVarargBool(vararg args: Boolean): String {
        return args.joinToString(", ")
    }
}

class TwineNativeTest {
    fun run(script: String): Any {
        val globals: Globals = JsePlatform.standardGlobals()

        val twineNative = TwineNativeTestClass()
        globals.set("test", twineNative.table)

        val result = globals.load(script, "test.lua").call()

        return result
    }

    @Test
    fun `testVarargStr should print the arguments passed to it`() {
        val result = run("return test.testVarargStr('Hello', 'World')")

        assertEquals("Hello, World", result.toString())
    }

    @Test
    fun `testVarargDouble should print the sum of all arguments`() {
        val result = run("return test.testVarargDouble(1, 2, 3)")

        assertEquals(6, result.toString().toInt())
    }

    @Test
    fun `testVarargBool should print the arguments passed to it`() {
        val result = run("return test.testVarargBool(true, false, true)")

        assertEquals("true, false, true", result.toString())
    }
}