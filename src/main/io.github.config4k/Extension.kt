package io.github.config4k

import com.typesafe.config.Config
import com.typesafe.config.ConfigException
import com.typesafe.config.ConfigFactory
import io.github.config4k.readers.SelectReader
import java.io.File
import java.nio.file.Path
import kotlin.reflect.full.primaryConstructor

/**
 * An extension function that enables you to use type parameter.
 *
 * Returns a value of given type by calling method
 * in [com.typesafe.config.Config]
 *
 * As this function is an inline function, shown stacktrace is not true.
 *
 * @param path see [com.typesafe.config.Config]
 */
inline fun <reified T> Config.extract(path: String): T {
    val genericType = object : TypeReference<T>() {}.genericType()

    val result = SelectReader.getReader(ClassContainer(T::class, genericType))(this, path)

    return try {
        result as T
    } catch (e: Exception) {
        throw result?.let { e } ?: ConfigException.BadPath(
                path, "take a look at your config")
    }
}

/**
 * Loads whole config into one data class.
 */
inline fun <reified T> Config.extract(): T {
    val genericType = object : TypeReference<T>() {}.genericType()

    val result = SelectReader.extractWithoutPath(ClassContainer(T::class, genericType), this)

    return try {
        result as T
    } catch (e: Exception) {
        throw e
    }
}

/**
 * Converts the receiver object to Config.
 *
 * @param name the returned config's name
 */
fun Any.toConfig(name: String): Config {
    val clazz = this.javaClass.kotlin
    for (customType in customTypeRegistry) {
        if (customType.testToConfig(this)) {
            return customType.toConfig(this, name)
        }
    }
    val map = when {
        clazz.javaPrimitiveType != null -> mapOf(name to this)
        this is String -> mapOf(name to this)
        this is Enum<*> -> mapOf(name to this.name)
        this is File -> mapOf(name to this.toString())
        this is Path -> mapOf(name to this.toString())
        this is Iterable<*> -> {
            val list = this.map {
                it?.toConfigValue()?.unwrapped()
            }
            mapOf(name to list)
        }
        this is Map<*, *> -> {
            val map = this.mapKeys {
                (it.key as? String) ?:
                        throw Config4kException.UnSupportedType(clazz)
            }.mapValues {
                it.value?.toConfigValue()?.unwrapped()
            }
            mapOf(name to map)
        }
        clazz.primaryConstructor != null ->
            mapOf(name to getConfigMap(this, clazz))
        clazz.objectInstance != null -> mapOf(name to emptyMap<String, Any>())
        else -> throw Config4kException.UnSupportedType(clazz)
    }

    return ConfigFactory.parseMap(map)
}
