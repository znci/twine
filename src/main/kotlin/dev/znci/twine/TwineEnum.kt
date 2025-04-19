package dev.znci.twine

import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue

class TwineEnum(private val enum: Enum<*>) : TwineTable(enum.javaClass.simpleName) {

    init {
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
        for (enumConstant in enum.javaClass.enumConstants) {
            table.set(enumConstant.name, TwineLuaValue(LuaValue.valueOf(enumConstant.ordinal)))
        }
        return table
    }

    fun fromLuaTable(luaTable: LuaTable): Enum<*> {
        val enumConstants = enum.javaClass.enumConstants
        for (i in 0 until luaTable.length()) {
            val name = luaTable[i + 1].toString()
            for (enumConstant in enumConstants as Array<Enum<*>>) {
                if (enumConstant.name == name) {
                    return enumConstant
                }
            }
        }
        throw TwineError("Enum constant not found")
    }
}