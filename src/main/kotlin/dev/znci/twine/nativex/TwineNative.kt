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
package dev.znci.twine.nativex

import dev.znci.twine.TwineTable

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
    override var valueName: String = ""
) : TwineTable(valueName) {
    companion object {
        val INHERIT_TAG = "INHERIT_FROM_DEFINITION"
    }

    /**
     * Initializes the TwineNative instance by registering its functions and properties.
     */
    init {
        val functionRegistrar = FunctionRegistrar(this)
        functionRegistrar.register()

        val propertyRegistrar = PropertyRegistrar(this)
        propertyRegistrar.registerProperties()
    }
}
