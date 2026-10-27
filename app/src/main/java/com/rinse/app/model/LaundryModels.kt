package com.rinse.app.model

data class LaundryMachine(
    val name: String,
    val type: String,
    val url: String
)

enum class LaundryStatus(val label: String) {
    WAITING("等待查询"),
    QUERYING("查询中"),
    AVAILABLE("可用"),
    BUSY("使用中"),
    UNAVAILABLE("不可用"),
    UNKNOWN("未知")
}

data class LaundryResult(
    val machine: LaundryMachine,
    val status: LaundryStatus
)
