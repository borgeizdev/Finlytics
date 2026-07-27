package com.borgeiz.meutcc2026.model

data class RecurringItem(
    var id: String = "",
    var title: String = "",
    var type: String = "",
    var amount: Double = 0.0,
    var category: String = "",
    var dayOfMonth: Int = 1
)

data class RecurringConfig(
    var items: List<RecurringItem> = emptyList()
)
