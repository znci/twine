package dev.znci.twine.nativex

import dev.znci.twine.TwineError
import org.luaj.vm2.LuaValue
import kotlin.reflect.KFunction

object ErrorHandler {
    /**
     * Throws a TwineError with the given cause.
     *
     * @param error The error to throw.
     * @param function The function that caused the error, if available.
     */
    fun throwError(error: Throwable, function: KFunction<*>? = null) {
        val cause = error.cause
        val errorPrefix = "Error calling ${function?.name}:"
        if (cause is TwineError) {
            cause.printStackTrace()
            LuaValue.error("$errorPrefix ${cause.message}")
        } else {
            var errorMessage = error.message

            if (errorMessage == null) {
                errorMessage = "Unexpected error"
            }

            error.printStackTrace()
            LuaValue.error("$errorPrefix $errorMessage")
        }
    }
}