package dev.znci.twine.nativex

import dev.znci.twine.annotations.TwineNativeProperty
import dev.znci.twine.nativex.conversion.Converter.toKotlinValue
import dev.znci.twine.nativex.conversion.Converter.toLuaValue
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.luaj.vm2.lib.ThreeArgFunction
import org.luaj.vm2.lib.TwoArgFunction
import kotlin.reflect.KMutableProperty
import kotlin.reflect.KProperty
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberProperties

class PropertyRegistrar(private val owner: TwineNative) {
    /**
     * Registers properties annotated with {@code TwineNativeProperty} into the Lua table.
     */
    fun registerProperties() {
        val properties = owner::class.memberProperties
            .mapNotNull { prop ->
                prop.findAnnotation<TwineNativeProperty>()?.let { annotation ->
                    val customName = annotation.name.takeIf { it != TwineNative.INHERIT_TAG } ?: prop.name
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
                    val value = prop.getter.call(owner)
                    value.toLuaValue()
                } catch (e: Exception) {
                    e.printStackTrace()
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
                    val setterParamType = prop.setter.parameters[1].type
                    val convertedValue = value.toKotlinValue(setterParamType)

                    prop.setter.call(owner, convertedValue)
                    TRUE
                } catch (e: Exception) {
                    error("Error setting '${prop.name}': ${e.message}")
                }
            }
        })

        owner.table.setmetatable(metatable)
    }
}