package com.organizador.scanner

// Kotlin 2.3 não aceita mais inferir arrayOf() com tipos misturados
// (String + Int + Long). Estes overloads deixam o tipo explícito para
// os argumentos de bind do SQLite, sem alterar os arrays de String usados
// em rawQuery e no seletor de arquivos.
fun arrayOf(a: String): Array<String> = kotlin.arrayOf(a)
fun arrayOf(a: String, b: String): Array<String> = kotlin.arrayOf(a, b)

fun arrayOf(a: Long): Array<Any?> = kotlin.arrayOf<Any?>(a)
fun arrayOf(a: String, b: Int, c: Int): Array<Any?> = kotlin.arrayOf<Any?>(a, b, c)
fun arrayOf(a: String, b: Int, c: Int, d: Long): Array<Any?> = kotlin.arrayOf<Any?>(a, b, c, d)
fun arrayOf(a: Long, b: String, c: Int, d: String, e: Long): Array<Any?> =
    kotlin.arrayOf<Any?>(a, b, c, d, e)
fun arrayOf(a: String, b: Int, c: String, d: Long): Array<Any?> =
    kotlin.arrayOf<Any?>(a, b, c, d)
