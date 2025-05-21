package dev.znci.twine.nativex

import dev.znci.twine.TwineError
import dev.znci.twine.annotations.TwineNativeFunction
import dev.znci.twine.nativex.conversion.Converter.toKotlinArgs
import dev.znci.twine.nativex.conversion.Converter.toKotlinType
import dev.znci.twine.nativex.conversion.Converter.toLuaValue
import org.luaj.vm2.Varargs
import org.luaj.vm2.lib.VarArgFunction
import java.lang.reflect.InvocationTargetException
import kotlin.reflect.KFunction
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.functions
import kotlin.reflect.full.isSupertypeOf

class FunctionRegistrar(private val owner: TwineNative) {
    val functions = owner::class.functions

    fun register() {
        registerFunctions()
        registerOverloads()
    }

    /**
     * Registers functions annotated with {@code TwineNativeFunction} into the Lua table.
     */
    fun registerFunctions() {
        functions.forEach { function ->
            if (function.findAnnotation<TwineNativeFunction>() == null)
                return@forEach

            // Set the name of the method based on the string given to the annotation
            val annotation = function.findAnnotation<TwineNativeFunction>()
            var annotatedFunctionName = annotation?.name ?: function.name

            if (annotatedFunctionName == TwineNative.INHERIT_TAG)
                annotatedFunctionName = function.name

            owner.table.set(annotatedFunctionName, object : VarArgFunction() {
                override fun invoke(args: Varargs): Varargs {
                    return try {
                        val kotlinArgs = args.toKotlinArgs(function)
                        val result = function.call(owner, *kotlinArgs)
                        result.toLuaValue()
                    } catch (e: InvocationTargetException) {
                        ErrorHandler.throwError(e, function)
                    } as Varargs
                }
            })
        }
    }

    /**
     * Handles overloaded functions.
     */
    fun registerOverloads() {
        val functionMap = mutableMapOf<String, MutableList<KFunction<*>>>()

        functions.forEach { function ->
            if (function.findAnnotation<TwineNativeFunction>() == null)
                return@forEach

            // Set the name of the method based on the string given to the annotation
            val annotation = function.findAnnotation<TwineNativeFunction>()
            var annotatedFunctionName = annotation?.name ?: function.name

            if (annotatedFunctionName == TwineNative.INHERIT_TAG)
                annotatedFunctionName = function.name
            functionMap.computeIfAbsent(annotatedFunctionName) { mutableListOf() }.add(function)
        }

        // Find a match depending on the arg count and the arg types
        functionMap.forEach { (name, overloadedFunctions) ->
            if (overloadedFunctions.isEmpty())
                throw TwineError("No overloaded functions found for $name")

            val overloadedFunction = object : VarArgFunction() {
                override fun invoke(args: Varargs): Varargs {
                    val argCount = args.narg()
                    val matchingFunction = overloadedFunctions.find { function ->
                        val params = function.parameters.drop(1) // Skip `this`
                        val isVararg = params.lastOrNull()?.isVararg == true
                        val fixedParamCount = if (isVararg) params.size - 1 else params.size

                        if (argCount < fixedParamCount) return@find false
                        if (!isVararg && argCount != fixedParamCount) return@find false

                        for (i in 0 until argCount) {
                            val paramType = params[i].type
                            val argType = args.arg(i + 1).toKotlinType()

                            if (!paramType.isSupertypeOf(argType)) {
                                return@find false
                            }
                        }
                        true
                    }

                    if (matchingFunction != null) {
                        val kotlinArgs = args.toKotlinArgs(matchingFunction)
                        return try {
                            val result = matchingFunction.call(this, *kotlinArgs)
                            result.toLuaValue()
                        } catch (e: InvocationTargetException) {
                            ErrorHandler.throwError(e, matchingFunction)
                        } as Varargs
                    } else {
                        if (overloadedFunctions.isNotEmpty()) {
                            val firstParam = overloadedFunctions.first().parameters.getOrNull(1)

                            if (firstParam?.isVararg == true) {
                                val varargFunction = overloadedFunctions.find {
                                    val ps = it.parameters.drop(1)
                                    ps.size == 1 && ps[0].isVararg
                                } ?: throw TwineError("No vararg function found")

                                val varargArgs = args.toKotlinArgs(varargFunction)

                                return try {
                                    val result = varargFunction.call(this, *varargArgs)
                                    result.toLuaValue()
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    ErrorHandler.throwError(e, varargFunction)
                                } as Varargs
                            }
                        } else {
                            throw TwineError("No matching function found for $name with $argCount arguments")
                        }
                    }

                    return NIL
                }
            }
            owner.table.set(name, overloadedFunction)
        }
    }
}