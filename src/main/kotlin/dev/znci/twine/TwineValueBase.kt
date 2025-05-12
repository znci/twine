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

/**
 * Base class for Twine API values, extending `TwineValue`.
 * This class provides a common structure for all Twine values, including a `valueName`
 * that identifies the value within the Twine API.
 * It serves as a superclass for other classes that represent values in the Twine API.
 *
 * @param valueName The name of the value, used for specifying the name of the value if it becomes a global.
 */
open class TwineValueBase(
    open var valueName: String = ""
): TwineLuaValue()