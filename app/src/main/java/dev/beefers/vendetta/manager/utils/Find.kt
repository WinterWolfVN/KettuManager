package dev.beefers.vendetta.manager.utils

fun <T> java.util.Enumeration<T>.find(predicate: (T) -> Boolean): T? {
    while (hasMoreElements()) {
        val element = nextElement()
        if (predicate(element)) return element
    }
    return null
}

fun <T> java.util.Enumeration<T>.indexOfLast(predicate: (T) -> Boolean): Int {
    val list = java.util.Collections.list(this)
    return list.indexOfLast(predicate)
}
