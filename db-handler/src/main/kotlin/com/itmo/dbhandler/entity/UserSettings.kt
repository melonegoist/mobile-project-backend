package com.itmo.dbhandler.entity

import jakarta.persistence.*
import java.time.OffsetDateTime

@Entity
@Table(name = "user_settings")
data class UserSettings(
    @Id
    @Column(name = "user_id")
    val userId: Long = 0,

    @Column(name = "notifications_email")
    var notificationsEmail: Boolean = true,

    @Column(name = "notifications_push")
    var notificationsPush: Boolean = true,

    @Column(name = "notifications_price_alerts")
    var notificationsPriceAlerts: Boolean = true,

    var language: String = "en",

    var theme: String = "system",

    @Column(name = "default_order_type")
    var defaultOrderType: String = "market",

    @Column(name = "risk_level")
    var riskLevel: String = "medium",

    @Column(name = "created_at")
    var createdAt: OffsetDateTime = OffsetDateTime.now(),

    @Column(name = "updated_at")
    var updatedAt: OffsetDateTime = OffsetDateTime.now()
)