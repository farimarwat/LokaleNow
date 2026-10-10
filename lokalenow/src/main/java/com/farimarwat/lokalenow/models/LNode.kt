package com.farimarwat.lokalenow.models

enum class NodeType {
    STRING,
    PLURALS,
    STRING_ARRAY
}

data class LNode(
    val name: String = "",
    val value: String = "",
    val translatable: Boolean = true,
    val type: NodeType = NodeType.STRING,
    val pluralItems: Map<String, String> = emptyMap(),
    val arrayItems: List<String> = emptyList(),
    val isCData: Boolean = false
)