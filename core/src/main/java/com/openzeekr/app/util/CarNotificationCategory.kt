package com.openzeekr.app.util

import com.openzeekr.app.net.model.InboxMessage
import java.util.Locale

/** Android channels for car messages. Unknown types stay on the existing FCM fallback channel. */
enum class CarNotificationCategory(val channelId: String) {
    CHARGING("car_charging"),
    SECURITY("car_security"),
    VEHICLE("car_vehicle"),
    UPDATES("car_updates"),
    SERVICE("car_service"),
    NEWS("car_news"),
    OTHER("car_alerts");

    companion object {
        fun forMessage(msg: InboxMessage): CarNotificationCategory {
            val hints = (msg.categoryHints + listOfNotNull(msg.category)).map(::normalize)
            // These backend groups are more reliable than words in their message titles.
            for (hint in hints) {
                when (hint) {
                    "ota" -> return UPDATES
                    "after sales" -> return SERVICE
                    "zeekr" -> return NEWS
                }
            }
            for (hint in hints) {
                val category = when (hint) {
                    "charging", "charge", "battery" -> CHARGING
                    "security" -> SECURITY
                    "updates", "software updates" -> UPDATES
                    "service", "maintenance" -> SERVICE
                    "news", "marketing" -> NEWS
                    else -> classifyTitle(hint)
                }
                if (category != OTHER) return category
            }
            val titleCategory = classifyTitle(normalize(msg.title.orEmpty()))
            if (titleCategory != OTHER) return titleCategory
            // VEHICLE also contains charging and security alerts, so it doesn't identify controls.
            return OTHER
        }

        // Match complete words after splitting identifiers such as chargeComplete / CHARGE_COMPLETE.
        // No numeric codes are inferred. Bodies and URLs can mention unrelated topics or carry ads.
        private fun normalize(value: String): String = value
            .replace(Regex("([A-Z])([A-Z][a-z])"), "$1 $2")
            .replace(Regex("([a-z0-9])([A-Z])"), "$1 $2")
            .lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()

        private fun classifyTitle(title: String): CarNotificationCategory {
            fun has(pattern: String) = Regex("\\b(?:$pattern)\\b").containsMatchIn(title)
            return when {
                has("newsletter|promotion|discount|offer|zeekr news") -> NEWS
                has("ota|firmware|software (?:update|upgrade)") -> UPDATES
                has("service (?:reminder|due|appointment)|maintenance|servicing") -> SERVICE
                (has("security|sentry|sentinel|intrusion|break in|theft|tamper(?:ing)?|tow(?:ing)?") &&
                    has("alert|alarm|warning|detected")) || has("(?:car|vehicle) (?:is being )?towed|abnormal parking") -> SECURITY
                title == "chaging status reminder" || has("charg(?:e|er|ing)|batter(?:y|ies)|state of charge") -> CHARGING
                has("remote control|climate|air conditioning|doors?|windows?|lock(?:ed)?|unlock(?:ed)?|trunk|frunk") -> VEHICLE
                else -> OTHER
            }
        }
    }
}
