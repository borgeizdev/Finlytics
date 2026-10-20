package com.borgeiz.meutcc2026.model

data class CategoryConfig(
    var income: List<String> = emptyList(),
    var expense: List<String> = emptyList(),
    var payment: List<String> = emptyList()
)
