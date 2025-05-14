package dev.znci.twine

import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import kotlin.reflect.KClass

open class TwineEnum(private val enum: KClass<*>) : TwineTable(enum.java.simpleName) {

    init {
        if (!enum.java.isEnum) {
            throw TwineError("TwineEnum can only be used with enum classes")
        }
        val table = this.table
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
            // FIXME: figure out why we can't use TwineLuaValue.
            table.set(enumConstant.name, LuaValue.valueOf(enumConstant.ordinal))
        }
        return table
    }

    fun fromLuaTable(luaTable: LuaTable): Enum<*> {
        val enumConstants = enum.java.enumConstants
        for (i in 0 until luaTable.length()) {
            val name = luaTable[i + 1].toString()
            @Suppress("UNCHECKED_CAST")
            for (enumConstant in enumConstants as Array<Enum<*>>) {
                if (enumConstant.name == name) {
                    return enumConstant
                }
            }
        }
        throw TwineError("Enum constant not found")
    }
}