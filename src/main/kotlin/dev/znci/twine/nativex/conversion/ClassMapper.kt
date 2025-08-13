package dev.znci.twine.nativex.conversion

import dev.znci.twine.TwineEnum
import dev.znci.twine.TwineError
import dev.znci.twine.TwineTable
import dev.znci.twine.nativex.conversion.Converter.toKotlinValue
import org.luaj.vm2.LuaTable
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.full.primaryConstructor

object ClassMapper {
    /**
     * Converts a LuaTable into a given class.
     */
    fun LuaTable.toClass(func: KFunction<*>): Any { // XXX: stupid trick to also return enums.
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
            } catch (_: ClassNotFoundException) {
                throw TwineError("Could not find class '$className'. Did you forget to set __javaClass?")
            }

            // if enum class
            if (clazz.java.isEnum) {
                // get Enum<*> class
                val renum = TwineEnum(clazz)
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
}
