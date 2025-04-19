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

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import org.luaj.vm2.LuaBoolean
import org.luaj.vm2.LuaInteger
import org.luaj.vm2.LuaNil
import org.luaj.vm2.LuaString
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import java.util.ArrayList

/**
 * A wrapper class for `LuaValue` that provides additional functionality and type safety.
 * This class extends `LuaValue` and delegates method calls to the wrapped `luaValue` instance.
 */
open class TwineLuaValue(val luaValue: LuaValue = LuaValue.TRUE) : LuaValue() {

    /**
     * Returns the type of the wrapped `LuaValue`.
     *
     * @return The integer type code of the `LuaValue`.
     */
    override fun type(): Int {
        return luaValue.type()
    }

    /**
     * Returns the type name of the wrapped `LuaValue`.
     *
     * @return The name of the Lua type as a string.
     */
    override fun typename(): String? {
        return luaValue.typename()
    }

    companion object {
        /**
         * Represents a `nil` Lua value.
         */
        val NIL = TwineLuaValue(LuaValue.NIL)

        /**
         * Represents a `true` Lua value.
         */
        val TRUE = TwineLuaValue(LuaValue.TRUE)

        /**
         * Represents a `false` Lua value.
         */
        val FALSE = TwineLuaValue(LuaValue.FALSE)

        /**
         * Converts a given Kotlin value to a `TwineValue` instance.
         *
         * @param value The value to be converted.
         * @return A corresponding `TwineValue` representing the input value.
         */
        fun valueOf(value: Any?): TwineLuaValue {
            return when (value) {
                is String -> TwineLuaValue(LuaValue.valueOf(value))
                is Boolean -> TwineLuaValue(LuaValue.valueOf(value))
                is Int -> TwineLuaValue(LuaValue.valueOf(value))
                is Double -> TwineLuaValue(LuaValue.valueOf(value))
                is TwineTable -> TwineLuaValue(value.table)
                is LuaValue -> TwineLuaValue(value)
                is ArrayList<*> -> {
                    val table = TwineTable("")

                    value.forEachIndexed { index, item ->
                        println("$index $item")
                        table.setSimple((index + 1).toString(), valueOf(item))
                    }

                    table
                }
                else -> {
                    throw TwineError("Unsupported Lua type: ${value?.javaClass?.simpleName ?: "null"}")
                    NIL
                }
            }
        }
    }

    /**
     * Converts a given JSON element to a corresponding `TwineValue` instance.
     *
     * @param jsonElement The JSON element to convert.
     * @return A `TwineValue` representing the JSON element.
     */
    fun fromJSON(jsonElement: JsonElement): TwineTable {
        try {
            return when {
                jsonElement.isJsonObject -> {
                    val table = TwineTable("")

                    for (entry in jsonElement.asJsonObject.entrySet()) {
                        val key = entry.key
                        val value = entry.value

                        when {
                            value.isJsonObject -> {
                                val nestedTable = fromJSON(value)
                                table.setSimple(key, nestedTable)
                            }
                            value.isJsonArray -> {
                                val luaArrayTable = handleJsonArray(value.asJsonArray)
                                table.setSimple(key, luaArrayTable)
                            }
                            value.isJsonPrimitive -> {
                                val primitive = value.asJsonPrimitive

                                handleJsonPrimitive(key, primitive, table)
                            }
                            value.isJsonNull -> {
                                table.setSimple(key, LuaValue.NIL)
                            }
                            else -> {
                                throw TwineError("Unknown value type for key: $key")
                            }
                        }
                    }

                    table
                }
                else -> {
                    TwineTable("")
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return TwineTable("")
        }
    }


    /**
     * Handles the conversion of a JSON array to a Lua compatible value, then stored in a TwineTable
     *
     * This function iterates over each element in an array (`JsonArray`) and converts each value to a Lua value.
     * It also recursively scans any objects or other arrays.
     *
     * @param key The name of the table key.
     * @param array The JSON array (`JsonArray`) to be converted.
     * @param table The `TwineTable` in which the converted Lua value will be stored.
     *
     * This function processes different types of JSON elements within the array:
     * - For primitive types (string, number, boolean), it converts them to their Lua representations.
     * - For nested JSON objects, it recursively calls `fromJSON` to process the object.
     * - For nested JSON arrays, it recursively calls `handleJsonArray` to process the array.
     */
    fun handleJsonArray(array: JsonArray): TwineTable {
        val table = TwineTable("")

        array.forEachIndexed { index, element ->
            val luaValue = when {
                element.isJsonPrimitive -> {
                    val primitive = element.asJsonPrimitive
                    when {
                        primitive.isString -> primitive.asString
                        primitive.isBoolean -> primitive.asBoolean
                        primitive.isNumber -> primitive.asNumber.toDouble()
                        else -> "Unknown"
                    }
                }
                element.isJsonObject -> fromJSON(element)
                element.isJsonArray -> handleJsonArray(element.asJsonArray)
                else -> "Unknown"
            }

            table.setSimple(index, luaValue)
        }

        return table
    }


    /**
     * Converts a JSON primitive to a TwineTable.
     *
     * @param key The name of the table key.
     * @param primitive The JSON primitive (`JsonPrimitive`) to be converted.
     * @param table The `TwineTable` in which the converted Lua value will be stored.
     *
     * This function handles three specific types of `JsonPrimitive`:
     * - String: Converts the JSON string to a Lua string.
     * - Boolean: Converts the JSON boolean to a Lua boolean.
     * - Number: Converts the JSON number to a Lua number.
     * If the primitive is of an unsupported type, it is stored as "Unknown".
     */
    fun handleJsonPrimitive(key: String, primitive: JsonPrimitive, table: TwineTable) {
        try {
            when {
                primitive.isString -> {
                    table.setSimple(key, primitive.asString)
                    "String"
                }
                primitive.isBoolean -> {
                    table.setSimple(key, primitive.asBoolean)
                    "Boolean"
                }
                primitive.isNumber -> {
                    table.setSimple(key, primitive.asNumber.toDouble())
                    "Number"
                }
                else -> {
                    table.setSimple(key, "Unknown")
                    "Unknown"
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Converts the wrapped `LuaValue` into a corresponding JSON representation.
     *
     * @param value The `TwineValue` to convert into JSON.
     * @return A `JsonElement` representing the Lua value.
     */
    fun toJSON(value: TwineLuaValue?): JsonElement {
        return when (luaValue) {
            is LuaTable -> {
                val jsonObject = JsonObject()
                for (key in luaValue.keys()) {
                    val luaKey = key ?: continue

                    jsonObject.add(luaKey.tojstring(), toJSON(value))
                }
                jsonObject
            }

            is LuaString -> {
                JsonPrimitive(luaValue.tojstring())
            }

            is LuaInteger -> {
                JsonPrimitive(luaValue.toint())
            }

            is LuaBoolean -> {
                JsonPrimitive(luaValue.toboolean())
            }

            is LuaNil -> {
                JsonNull.INSTANCE
            }

            else -> {
                JsonNull.INSTANCE
            }
        } as JsonElement
    }

    override fun toString(): String {
        return when (luaValue) {
            is LuaBoolean -> "LuaBoolean: ${luaValue.toboolean()}"
            is LuaString -> "LuaString: ${luaValue.tojstring()}"
            is LuaInteger -> "LuaInteger: ${luaValue.toint()}"
            else -> "Unknown LuaValue type: ${luaValue::class.simpleName}"
        }
    }
}