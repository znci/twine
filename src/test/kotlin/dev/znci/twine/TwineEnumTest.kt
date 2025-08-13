package dev.znci.twine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue

class TwineEnumTest {

    /**
     * Class being tested: TwineEnum
     *
     * Description:
     * The TwineEnum class wraps an Enum and exposes functionality for converting the Enum to a LuaTable.
     * The `toLuaTable()` method converts enum constants to a LuaTable where each constant is a field
     * with the name of the enum constant as the key, and its ordinal as the value.
     */

    private enum class SampleEnum {
        FIRST, SECOND, THIRD
    }

    @Test
    fun `toLuaTable should convert an enum with multiple constants to a LuaTable`() {
        // Arrange
//        val enum = SampleEnum.entries[0]
        val twineEnum = TwineEnum(SampleEnum::class)

        // Act
        val luaTable = twineEnum.toLuaTable()

        // Assert
        assertEquals(4, luaTable.keys().size) // 4, including the __javaClass key
        assertEquals(LuaValue.valueOf(0), luaTable.get("FIRST").get("enumOrdinal"))
        assertEquals(LuaValue.valueOf(1), luaTable.get("SECOND").get("enumOrdinal"))
        assertEquals(LuaValue.valueOf(2), luaTable.get("THIRD").get("enumOrdinal"))
    }

    @Test
    fun `fromLuaTable should convert a LuaTable with multiple constants back to the corresponding enum value`() {
        // Arrange
        val twineEnum = TwineEnum(SampleEnum::class)
        val luaTable = LuaTable()
        luaTable.set(1, LuaValue.valueOf("SECOND"))

        // Act
        val result = twineEnum.fromLuaTable(luaTable)

        // Assert
        assertEquals(SampleEnum.SECOND, result)
    }

    @Test
    fun `fromLuaTable should properly handle a LuaTable with a single constant`() {
        // Arrange
        val twineEnum = TwineEnum(SingleValueEnum::class)
        val luaTable = LuaTable()
        luaTable.set(1, LuaValue.valueOf("FIRST"))

        // Act
        val result = twineEnum.fromLuaTable(luaTable)

        // Assert
        assertEquals(SingleValueEnum.FIRST, result)
    }

    @Test
    fun `fromLuaTable should throw TwineError if the LuaTable contains an invalid enum constant`() {
        // Arrange
        val twineEnum = TwineEnum(SampleEnum::class)
        val luaTable = LuaTable()
        luaTable.set(1, LuaValue.valueOf("INVALID_CONSTANT"))

        // Act & Assert
        val exception = assertThrows<TwineError> {
            twineEnum.fromLuaTable(luaTable)
        }
        assertEquals("Enum constant not found", exception.message)
    }

    @Test
    fun `toLuaTable should handle an enum with a single constant correctly`() {
        // Arrange
        val twineEnum = TwineEnum(SingleValueEnum::class)

        // Act
        val luaTable = twineEnum.toLuaTable()

        // Assert
        assertEquals(2, luaTable.keys().size) // 2, including the __javaClass key
        assertEquals(LuaValue.valueOf(0), luaTable.get("FIRST").get("enumOrdinal"))
    }

    @Test
    fun `toLuaTable should handle an empty enum correctly`() {
        // Arrange
        val twineEnum = TwineEnum(EmptyEnum::class)

        // Act
        val luaTable = twineEnum.toLuaTable()

        // Assert
        assertEquals(1, luaTable.keys().size) // including the __javaClass key
    }

    private enum class SingleValueEnum {
        FIRST
    }

    private enum class EmptyEnum
}