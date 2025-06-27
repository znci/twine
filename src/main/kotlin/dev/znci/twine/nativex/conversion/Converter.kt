package dev.znci.twine.nativex.conversion

import dev.znci.twine.TableSetOptions
import dev.znci.twine.TwineEnum
import dev.znci.twine.TwineEnumValue
import dev.znci.twine.TwineError
import dev.znci.twine.TwineLuaValue
import dev.znci.twine.TwineTable
import dev.znci.twine.nativex.conversion.ClassMapper.toClass
import org.luaj.vm2.LuaBoolean
import org.luaj.vm2.LuaInteger
import org.luaj.vm2.LuaString
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.luaj.vm2.Varargs
import kotlin.collections.get
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KType
import kotlin.reflect.KTypeProjection
import kotlin.reflect.full.createType

object Converter {
    /**
     * Converts Lua arguments to Kotlin arguments based on function parameter types.
     *
     * @param func The function whose parameters should be converted.
     * @return An array of Kotlin compatible arguments.
     */
    fun Varargs.toKotlinArgs(func: KFunction<*>): Array<Any?> {
        val params = func.parameters.drop(1) // Skip `this`

        val lastParam = params.lastOrNull()
        val isVararg = lastParam?.isVararg == true
        val fixedParamCount = if (isVararg) params.size - 1 else params.size

        if (narg() < fixedParamCount) {
            throw TwineError("Not enough arguments for ${func.name}. Expected ${fixedParamCount}, got ${narg()}")
        }

        if (!isVararg && narg() != fixedParamCount) {
            throw TwineError("Invalid number of arguments for ${func.name}. Expected ${fixedParamCount}, got ${narg()}")
        }

        if (lastParam?.isVararg == true) {
            val firstArg = this.arg(1)
            val firstArgType = firstArg.toKotlinType()
            val count = narg()

            val firstArgArray: Any = when (firstArgType.classifier) {
                Int::class -> List(count) { this.arg(it + 1).toint() }.toIntArray().toTypedArray()
                Double::class -> List(count) { this.arg(it + 1).todouble() }.toDoubleArray()
                Boolean::class -> List(count) { this.arg(it + 1).toboolean() }.toBooleanArray()
                Float::class -> List(count) { this.arg(it + 1).tofloat() }.toFloatArray()
                Long::class -> List(count) { this.arg(it + 1).tolong() }.toLongArray()
                String::class -> Array(count) { this.arg(it + 1).tojstring() }
                LuaValue::class -> Array(count) { this.arg(it + 1) }

                else -> throw TwineError("Unsupported vararg type: $firstArgType")
            }

            return arrayOf(firstArgArray)
        }

        if (isVararg) {
            val varargParam = params.last()
            val varargArgs = ArrayList<Any?>()
            for (i in fixedParamCount until narg()) {
                val arg = this.arg(i + 1)
                varargArgs.add(arg.toKotlinValue(varargParam.type))
            }
            return arrayOf(varargArgs.toTypedArray())
        }

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
     * Converts a LuaValue to a Kotlin compatible value based on its type.
     *
     * @param type The expected Kotlin type.
     * @return The converted value in Kotlin.
     */
    fun LuaValue.toKotlinValue(type: KType?): Any? {
        if (isnil()) return null
        val classifier = type?.classifier as? KClass<*>

        val converters: Map<KClass<*>, () -> Any?> = mapOf(
            String::class to { if (isnil()) null else tojstring() },
            Boolean::class to { toboolean() },
            Int::class to { toint() },
            Double::class to { todouble() },
            Float::class to { tofloat() },
            Long::class to { tolong() }
        )

        if (isfunction()) {
            val luaFunc = this.checkfunction()
            return when (classifier) {
                Function0::class -> {
                    val fn: Function0<Any?> = {
                        val result = luaFunc.call()
                        result.toKotlinValue(null)
                    }
                    fn
                }

                Function1::class -> {
                    val fn: Function1<Any?, Any?> = { arg1 ->
                        val result = luaFunc.call(arg1.toLuaValue())
                        result.toKotlinValue(null)
                    }
                    fn
                }

                Function2::class -> {
                    val fn: Function2<Any?, Any?, Any?> = { arg1, arg2 ->
                        val result = luaFunc.call(arg1.toLuaValue(), arg2.toLuaValue())
                        result.toKotlinValue(null)
                    }
                    fn
                }

                Function3::class -> {
                    val fn: Function3<Any?, Any?, Any?, Any?> = { arg1, arg2, arg3 ->
                        val result = luaFunc.call(arg1.toLuaValue(), arg2.toLuaValue(), arg3.toLuaValue())
                        result.toKotlinValue(null)
                    }
                    fn
                }

                else -> {
                    throw TwineError("Unsupported function type: $classifier")
                }
            }
        }

        if (classifier == TwineEnumValue::class && istable()) {
            val table = checktable()
            val ordinal = table.get("enumOrdinal").toint()
            val name = table.get("enumName").tojstring()
            val parent = table.get("parentName").tojstring()
            val clazz = table.get("__javaClass").tojstring()

            return TwineEnumValue(parent, name, ordinal, clazz)
        }

        converters[classifier]?.let { return it() }

        return this
    }

    /**
     * Converts a Kotlin value to a LuaValue.
     * This function is mostly used for figuring out the return type of a function.
     * Such as a TwineNativeFunction that returns a TwineTable,
     * which would be converted to a lua-compatible LuaTable.
     *
     * @return The corresponding LuaValue.
     */
    fun Any?.toLuaValue(): LuaValue {
        return when (this) {
            is String -> LuaValue.valueOf(this)
            is Boolean -> LuaValue.valueOf(this)
            is Double -> LuaValue.valueOf(this.toDouble())
            is Int -> LuaValue.valueOf(this)
            is Long -> LuaValue.valueOf(this.toInt())
            is Float -> LuaValue.valueOf(this.toDouble())
            is LuaString -> LuaValue.valueOf(this.toString())
            is LuaInteger -> LuaValue.valueOf(this.toint())
            is LuaBoolean -> LuaValue.valueOf(this.toboolean())
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
            is Set<*> -> {
                val table = LuaTable()
                this.forEachIndexed { index, value ->
                    table.set(index + 1, value.toLuaValue())
                }
                table
            }
            is java.util.ArrayList<*> -> {
                val table = LuaTable()
                this.forEachIndexed { index, value ->
                    table.set(index + 1, value.toLuaValue())
                }
                table
            }
            is Enum<*> -> {
                val enumClass = this::class
                val enumTable = TwineEnum(enumClass)
                enumTable.toLuaTable()
            }
            null, Unit -> TwineLuaValue.NIL
            else -> {
                throw TwineError("Unsupported toLuaValue type: ${this.javaClass.simpleName ?: "null"}")
            }
        }
    }

    /**
     * Converts a Lua value to the corresponding Kotlin type.
     */
    fun LuaValue.toKotlinType(): KType {
        return when {
            isboolean() -> Boolean::class.createType()
            isnumber() -> Double::class.createType()
            isint() -> Int::class.createType()
            isstring() -> String::class.createType()
            isfunction() -> {
                Function::class.createType(listOf(KTypeProjection.STAR))
            }
            istable() -> {
                try {
                    val table = checktable()
                    val className = table.get("__javaClass").tojstring()
                    val clazz = Class.forName(className).kotlin
                    clazz.createType()
                } catch (e: ClassNotFoundException) {
                    throw TwineError("Could not find class for Lua table: ${e.message}")
                }
            }

            else -> this::class.createType()
        }
    }
}