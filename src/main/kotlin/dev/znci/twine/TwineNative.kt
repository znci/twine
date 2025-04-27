/**
 * Copyright 2025 znci
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dev.znci.twine

import dev.znci.twine.annotations.TwineNativeFunction
import dev.znci.twine.annotations.TwineNativeProperty
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.luaj.vm2.Varargs
import org.luaj.vm2.lib.ThreeArgFunction
import org.luaj.vm2.lib.TwoArgFunction
import org.luaj.vm2.lib.VarArgFunction
import java.lang.reflect.InvocationTargetException
import java.util.ArrayList
import kotlin.jvm.java
import kotlin.reflect.*
import kotlin.reflect.full.createType
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.functions
import kotlin.reflect.full.isSupertypeOf
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor

/**
 * Abstract class TwineNative serves as a bridge between Kotlin and Lua, allowing functions and properties
 * to be dynamically registered in a Lua table.
 *
 * Code is written as Kotlin, and is converted to Lua if the appropriate function/property has the correct annotation.
 *
 * Functions with the {@code TwineNativeFunction} annotation will be registered, and properties with the {@code TwineNativeProperty} annotation.
 */
@Suppress("unused")
abstract class TwineNative(
    /** The name of the Lua table/property for this object. */
    override var valueName: String
) : TwineTable(valueName) {

    /**
     * Initializes the TwineNative instance by registering its functions and properties.
     */
    init {
        registerFunctions(this.table)
        registerProperties(this.table)
    }

    /**
     * Registers functions annotated with {@code TwineNativeFunction} into the Lua table.
     *
     * @param table The LuaTable instance to register functions to.
     */
    private fun registerFunctions(table: LuaTable) {
        this::class.functions.forEach { function ->
            if (function.findAnnotation<TwineNativeFunction>() == null) {
                return@forEach
            }
            val annotation = function.findAnnotation<TwineNativeFunction>()
            var annotatedFunctionName = annotation?.name ?: function.name

            if (annotatedFunctionName == "INHERIT_FROM_DEFINITION" ) {
                annotatedFunctionName = function.name
            }

            table.set(annotatedFunctionName, object : VarArgFunction() {
                override fun invoke(args: Varargs): Varargs {
                    return try {
                        val kotlinArgs = args.toKotlinArgs(function)
                        val result = function.call(this@TwineNative, *kotlinArgs)
                        result.toLuaValue()
                    } catch (e: InvocationTargetException) {
                        throwError(e, function)
                    } as Varargs
                }
            })
        }
        handleOverloadedFunctions(this::class.functions)
    }

    /**
     * Handles overloaded functions.
     *
     * @param functions The list of functions to handle.
     */
    private fun handleOverloadedFunctions(functions: Collection<KFunction<*>>) {
        val functionMap = mutableMapOf<String, MutableList<KFunction<*>>>()

        functions.forEach { function ->
            if (function.findAnnotation<TwineNativeFunction>() == null) {
                return@forEach
            }
            val annotation = function.findAnnotation<TwineNativeFunction>()
            var annotatedFunctionName = annotation?.name ?: function.name

            if (annotatedFunctionName == "INHERIT_FROM_DEFINITION" ) {
                annotatedFunctionName = function.name
            }

            functionMap.computeIfAbsent(annotatedFunctionName) { mutableListOf() }.add(function)
        }

        // Find a match depending on the arg count and the arg types
        functionMap.forEach { (name, overloadedFunctions) ->
            if (overloadedFunctions.size > 1) {
                val overloadedFunction = object : VarArgFunction() {
                    override fun invoke(args: Varargs): Varargs {
                        val argCount = args.narg()
                        val matchingFunction = overloadedFunctions.find { function ->
                            val params = function.parameters.drop(1) // Skip `this`
                            if (params.size != argCount) {
                                return@find false
                            }
                            for (i in 0 until argCount) {
                                val paramType = params[i].type
                                val argType = getKotlinType(args.arg(i + 1), function)

                                if (!paramType.isSupertypeOf(argType)) {
                                    return@find false
                                }
                            }
                            true
                        }

                        if (matchingFunction != null) {
                            val kotlinArgs = args.toKotlinArgs(matchingFunction)
                            return try {
                                val result = matchingFunction.call(this@TwineNative, *kotlinArgs)
                                result.toLuaValue()
                            } catch (e: InvocationTargetException) {
                                throwError(e, matchingFunction)
                            } as Varargs
                        } else {
                            throw TwineError("No matching function found for $name with $argCount arguments")
                        }

                        return NIL
                    }
                }
                table.set(name, overloadedFunction)
            }
        }
    }
    /**
     * Registers properties annotated with {@code TwineNativeProperty} into the Lua table.
     *
     * @param table The LuaTable instance to register properties to.
     */
    private fun registerProperties(table: LuaTable) {
        val properties = this::class.memberProperties
            .mapNotNull { prop ->
                prop.findAnnotation<TwineNativeProperty>()?.let { annotation ->
                    val customName = annotation.name.takeIf { it != "INHERIT_FROM_DEFINITION" } ?: prop.name
                    customName to prop
                }
            }.toMap()

        val metatable = LuaTable()

        // Handle property getting
        metatable.set("__index", object : TwoArgFunction() {
            override fun call(self: LuaValue, key: LuaValue): LuaValue {
                val prop = properties[key.tojstring()] as? KProperty<*>
                    ?: return error("No property '${key.tojstring()}'")

                return try {
                    val value = prop.getter.call(this@TwineNative)
                    value.toLuaValue()
                } catch (e: Exception) {
                    error("Error getting '${prop.name}': ${e.message}")
                }
            }
        })

        // Handle property setting
        metatable.set("__newindex", object : ThreeArgFunction() {
            override fun call(self: LuaValue, key: LuaValue, value: LuaValue): LuaValue {
                val prop = properties[key.tojstring()] as? KMutableProperty<*>
                    ?: return error("No property '${key.tojstring()}'")

                return try {
                    prop.setter.call(this@TwineNative, value.toKotlinValue(prop.returnType))
                    TRUE
                } catch (e: Exception) {
                    error("Error setting '${prop.name}': ${e.message}")
                }
            }
        })

        table.setmetatable(metatable)
    }

    /**
     * Converts Lua arguments to Kotlin arguments based on function parameter types.
     *
     * @param func The function whose parameters should be converted.
     * @return An array of Kotlin compatible arguments.
     */
    private fun Varargs.toKotlinArgs(func: KFunction<*>): Array<Any?> {
        val params = func.parameters.drop(1) // Skip `this`
        return params.mapIndexed { index, param ->
            this.arg(index + 1).let { arg ->
                if (arg.istable()) {
                    arg.checktable().toClass(func)
                } else {
                    arg.toKotlinValue(param.type)
                }
            }
        }.toTypedArray()
    }

    /**
     * Converts a Lua value to the corresponding Kotlin type.
     */
    private fun getKotlinType(luaValue: LuaValue, func: KFunction<*>): KType {
        return when {
            luaValue.isboolean() -> Boolean::class.createType()
            luaValue.isnumber() -> Double::class.createType()
            luaValue.isint() -> Int::class.createType()
            luaValue.isstring() -> String::class.createType()
            luaValue.isfunction() -> Function::class.createType()
            luaValue.istable() -> {
                try {
                    val instance = luaValue.checktable().toClass(func)
                    instance::class.createType()
                } catch (e: Exception) {
                    LuaTable::class.createType()
                }
            }
            else -> luaValue::class.createType()
        }
    }

    /**
     * Converts a LuaValue to a Kotlin compatible value based on its type.
     *
     * @param type The expected Kotlin type.
     * @return The converted value in Kotlin.
     */
    private fun LuaValue.toKotlinValue(type: KType?): Any? {
        return when {
            isfunction() -> {
                val func = checkfunction()

                // TODO: make this nicer
                return when (type?.classifier) {
                    Function0::class -> {
                        func.call()
                    }
                    Function1::class -> { arg1: Any? ->
                        func.call(arg1.toLuaValue())
                    }
                    Function2::class -> { arg1: Any?, arg2: Any? ->
                        func.call(arg1.toLuaValue(), arg2.toLuaValue())
                    }
                    Function3::class -> { arg1: Any?, arg2: Any?, arg3: Any? ->
                        func.call(arg1.toLuaValue(), arg2.toLuaValue(), arg3.toLuaValue())
                    }

                    else -> func
                }
            }
            type?.classifier == String::class -> if (isnil()) null else tojstring()
            type?.classifier == Boolean::class -> toboolean()
            type?.classifier == Int::class -> toint()
            type?.classifier == Double::class -> todouble()
            type?.classifier == Float::class -> tofloat()
            type?.classifier == Long::class -> tolong()
            else -> this
        }
    }

    /**
     * Converts a Kotlin value to a LuaValue.
     *
     * @return The corresponding LuaValue.
     */
    private fun Any?.toLuaValue(): LuaValue {
        return when (this) {
            is String -> LuaValue.valueOf(this)
            is Boolean -> LuaValue.valueOf(this)
            is Double -> LuaValue.valueOf(this.toDouble())
            is Int -> LuaValue.valueOf(this)
            is Float -> LuaValue.valueOf(this.toDouble())
            is Long -> LuaValue.valueOf(this.toDouble())
            is TwineTable -> {
                val table = this.table
                set("__javaClass", TableSetOptions(getter = { LuaValue.valueOf(javaClass.name) }))
                table
            }
            is TwineLuaValue -> {
                throw TwineError("TwineLuaValue should not be used as a return type.")
            }
            is Array<*> -> {
                val table = LuaTable()
                this.forEachIndexed { index, value ->
                    table.set(index + 1, value.toLuaValue())
                }
                table
            }
            is ArrayList<*> -> {
                val table = LuaTable()
                this.forEachIndexed { index, value ->
                    table.set(index + 1, value.toLuaValue())
                }
                table
            }
            is Enum<*> -> {
                val enumClass = this::class
                val enumTable = TwineEnum(enumClass as Enum<*>)
                enumTable.toLuaTable()
            }
            is Unit -> {
                NIL
            }
            null -> {
                NIL
            }
            else -> {
                throw TwineError("Unsupported type: ${this.javaClass.simpleName ?: "null"}")
            }
        }
    }

    /**
     * Converts a LuaTable into a given class.
     */
    private fun LuaTable.toClass(func: KFunction<*>): Any { // XXX: stupid trick to also return enums.
        try {
            var className = get("__javaClass").tojstring()

            if (className == "nil") {
                var classes = func.parameters.map { it.type.classifier }
                classes = classes.drop(1)
                val tableProps = keys().asSequence().map { it.tojstring() }.toList()
                for (clazz in classes) {
                    val constructor = (clazz as KClass<*>).primaryConstructor
                    if (constructor != null) {
                        val constructorProps = constructor.parameters.map { it.name }
                        if (constructorProps.containsAll(tableProps)) {
                            className = clazz.qualifiedName!!
                            break
                        }
                    }
                }

            }

            val clazz = try {
                Class.forName(className).kotlin
            } catch (e: ClassNotFoundException) {
                throw TwineError("Could not find class '$className'. Did you forget to set __javaClass?")
            }

            // if enum class
            if (clazz.java.isEnum) {
                // get Enum<*> class
                val renum = TwineEnum(clazz as Enum<*>)
                return renum.fromLuaTable(this)
            } else {
                val constructor =
                    clazz.primaryConstructor
                        ?: throw IllegalArgumentException("No primary constructor found for $className")
                val args = constructor.parameters.map { param ->
                    val value = get(param.name)
                    value.toKotlinValue(param.type)
                }.toTypedArray()
                return constructor.call(*args) as TwineTable
            }
        } catch (e: Exception) {
            throw e
        }
    }

    /**
     * Throws a TwineError with the given cause.
     *
     * @param error The error to throw.
     * @param function The function that caused the error, if available.
     */
    private fun throwError(error: Throwable, function: KFunction<*>? = null) {
        val cause = error.cause
        val errorPrefix = "Error calling ${function?.name}:"
        if (cause is TwineError) {
            cause.printStackTrace()
            error("$errorPrefix ${cause.message}")
        } else {
            var errorMessage = error.message

            if (errorMessage == null) {
                errorMessage = "Unexpected error"
            }

            error.printStackTrace()
            error("$errorPrefix $errorMessage")
        }
    }
}