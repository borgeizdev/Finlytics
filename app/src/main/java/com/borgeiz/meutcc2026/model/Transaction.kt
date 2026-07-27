package com.borgeiz.meutcc2026.model

import com.google.firebase.database.Exclude

data class Transaction(
    @get:Exclude @set:Exclude var id: String = "",
    var type: String = "",
    var title: String = "",
    var amount: Double = 0.0,
    var category: String = "",
    var date: String = "",
    var description: String = "",
    var paymentMethod: String = ""
)
