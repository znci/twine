package dev.znci.twine

import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import kotlin.reflect.KClass

class TwineEnum(name: String) : TwineTable(name) {
    fun toLuaTable(enum: Enum<*>): LuaTable {
        val table = this.table
        for (enumConstant in enum.javaClass.enumConstants) {
            table.set(enumConstant.name, TwineLuaValue(LuaValue.valueOf(enumConstant.ordinal)))
        }
        return table
    }

    fun fromLuaTable(luaTable: LuaTable, enum: KClass<out Any>): Enum<*> {
        val enumConstants = enum.java.enumConstants
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