package br.com.inforprimetecnologia.vaidebuzao.core.text

import java.text.Normalizer

object TextNormalizer {
    private val MARKS = "\\p{Mn}+".toRegex()
    private val NON_ALNUM = "[^a-z0-9]+".toRegex()

    /** Remove acentos, deixa minúsculo e troca pontuação por espaço: "Várzea-Grande" -> "varzea grande". */
    fun normalize(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(MARKS, "")
            .lowercase()
            .replace(NON_ALNUM, " ")
            .trim()

    /** Todas as palavras da consulta aparecem (como prefixo de alguma palavra) no texto. */
    fun matches(normalizedText: String, normalizedQuery: String): Boolean {
        if (normalizedQuery.isBlank()) return false
        val words = normalizedText.split(' ')
        return normalizedQuery.split(' ').all { q -> words.any { it.startsWith(q) } }
    }
}
