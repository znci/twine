package dev.znci.twine

import dev.znci.twine.annotations.TwineNativeProperty
import dev.znci.twine.nativex.TwineNative
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import kotlin.reflect.KClass

@Suppress("unused")
open class TwineEnum(private val enum: KClass<*>) : TwineTable(enum.java.simpleName) {
    init {
        if (!enum.java.isEnum) {
            throw TwineError("TwineEnum can only be used with enum classes")
        }

        table.setmetatable(LuaTable())
        table.set("__index", object : LuaValue() {
            override fun type(): Int = TTABLE
            override fun typename(): String = "table"
            override fun call(key: LuaValue): LuaValue {
                return table.get(key)
            }
        })
        table.set("__index", object : LuaValue() {
            override fun type(): Int = TTABLE
            override fun typename(): String = "table"
            override fun call(key: LuaValue, value: LuaValue): LuaValue {
                throw TwineError("Enum values cannot be set")
            }
        })
    }

    fun toLuaTable(): LuaTable {
        val table = this.table
        @Suppress("UNCHECKED_CAST")
        for (enumConstant in enum.java.enumConstants as Array<Enum<*>>) {
            table.set(enumConstant.name, TwineEnumValue(
                enumConstant.declaringJavaClass.name,
                enumConstant.name,
                enumConstant.ordinal,
                TwineEnumValue::class.java.name
            ).table)
        }
        return table
    }

    fun fromLuaTable(luaTable: LuaTable): Enum<*> {
        for (i in 0 until luaTable.length()) {
            val name = luaTable[i + 1].toString()
            for (enumConstant in getConstants()) {
                if (enumConstant.name == name) {
                    return enumConstant
                }
            }
        }
        throw TwineError("Enum constant not found")
    }

    fun getEnumValue(ordinal: Int): Any? {
        val constants = enum.java.enumConstants?.toList() ?: throw TwineError("getEnumValue was called without an enum class")
        val constant = constants.getOrNull(ordinal) ?: throw TwineError("getEnumValue was called without a valid ordinal")

        return try {
            val field = constant.javaClass.getDeclaredField("bukkitValue")
            field.isAccessible = true
            field.get(constant)
        } catch (_: Exception) {
            constant
        }
    }

    fun getValue(ordinal: Int): TwineEnumValue {
        val constant = getConstants().getOrNull(ordinal)
            ?: throw TwineError("Invalid ordinal")

        return TwineEnumValue(
            constant.declaringJavaClass.name,
            constant.name,
            constant.ordinal,
            TwineEnumValue::class.java.name
        )
    }

    fun getConstants(): Array<Enum<*>> {
        @Suppress("UNCHECKED_CAST")
        return enum.java.enumConstants as Array<Enum<*>>
    }

    fun isValidValue(value: TwineEnumValue): Boolean {
        if (enum.simpleName != value.parent) return false

        val constants = getConstants()
        if (value.ordinal < 0 || value.ordinal > constants.size) return false

        return constants[value.ordinal].name == value.name
    }

    fun toJava(value: TwineEnumValue): Enum<*> {
        if (!isValidValue(value)) {
            throw TwineError("Invalid enum value: ${value.name} in ${value.parent}")
        }
        return getConstants()[value.ordinal]
    }

    companion object {
        fun Enum<*>.toLua(): TwineEnumValue {
            return TwineEnumValue(
                this.declaringJavaClass.name,
                this.name,
                this.ordinal,
                TwineEnumValue::class.java.name
            )
        }

        fun <T : Enum<T>> KClass<T>.toLua(): TwineEnum {
            return TwineEnum(this)
        }
    }
}

@Suppress("unused")
open class TwineEnumValue(
    private val parentName: String,
    private val enumName: String,
    private val enumOrdinal: Int,
    private val __javaClass: String
) : TwineNative() {
    @TwineNativeProperty("__javaClass")
    val javaClass: String
        get() = __javaClass

    @TwineNativeProperty("parentName")
    val parent: String
        get() = parentName

    @TwineNativeProperty("enumName")
    val name: String
        get() = enumName

    @TwineNativeProperty("enumOrdinal")
    val ordinal: Int
        get() = enumOrdinal

    fun toJava(): Enum<*> {
        val enumClass = Class.forName(parentName).kotlin
        return TwineEnum(enumClass).getEnumValue(enumOrdinal) as Enum<*>
    }
}

