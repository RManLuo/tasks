package org.tasks.billing

interface PurchaseState {
    val hasTasksAccount: Boolean
    val hasPro: Boolean
    val hasTasksSubscription: Boolean
    val canUseCustomIcons: Boolean get() = hasPro
    fun purchasedThemes(): Boolean = hasPro
}
