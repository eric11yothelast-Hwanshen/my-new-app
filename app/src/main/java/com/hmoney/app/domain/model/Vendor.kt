package com.hmoney.app.domain.model

import com.hmoney.app.R

/**
 * 手機廠牌定義：偵測 Build.MANUFACTURER 後顯示對應的
 * 白名單設定步驟（依 dontkillmyapp.com 整理）與可跳轉的設定頁。
 */
data class Vendor(
    val displayName: String,
    val slug: String,
    val stepsRes: Int,
    val intentComponents: List<Pair<String, String>>,
)

fun detectVendor(manufacturer: String?): Vendor? {
    val name = (manufacturer ?: "").lowercase()
    return when {
        name.contains("samsung") -> Vendor(
            displayName = "Samsung",
            slug = "samsung",
            stepsRes = R.string.wizard_steps_samsung,
            intentComponents = listOf(
                "com.samsung.android.lool" to "com.samsung.android.sm.battery.ui.BatteryActivity",
                "com.samsung.android.lool" to "com.samsung.android.lool.activity.SmartManagerActivity",
                "com.samsung.android.sm.battery" to "com.samsung.android.sm.battery.ui.BatteryActivity",
            ),
        )
        name.contains("xiaomi") || name.contains("redmi") || name.contains("poco") -> Vendor(
            displayName = "Xiaomi",
            slug = "xiaomi",
            stepsRes = R.string.wizard_steps_xiaomi,
            intentComponents = listOf(
                "com.miui.securitycenter" to
                    "com.miui.permcenter.autostart.AutoStartManagementActivity",
                "com.miui.powerkeeper" to
                    "com.miui.powerkeeper.ui.HiddenAppsConfigActivity",
            ),
        )
        name.contains("oppo") || name.contains("realme") || name.contains("oneplus") -> Vendor(
            displayName = "OPPO",
            slug = "oppo",
            stepsRes = R.string.wizard_steps_oppo,
            intentComponents = listOf(
                "com.coloros.safecenter" to
                    "com.coloros.safecenter.permission.startup.StartupAppListActivity",
                "com.oppo.safe" to "com.oppo.safe.permission.startup.StartupAppListActivity",
                "com.coloros.oppoguardelf" to
                    "com.coloros.powermanager.fuelgaue.PowerUsageModelActivity",
            ),
        )
        name.contains("vivo") || name.contains("iqoo") -> Vendor(
            displayName = "vivo",
            slug = "vivo",
            stepsRes = R.string.wizard_steps_vivo,
            intentComponents = listOf(
                "com.vivo.permissionmanager" to
                    "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
                "com.iqoo.secure" to "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager",
            ),
        )
        name.contains("huawei") -> Vendor(
            displayName = "Huawei",
            slug = "huawei",
            stepsRes = R.string.wizard_steps_huawei,
            intentComponents = listOf(
                "com.huawei.systemmanager" to
                    "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
                "com.huawei.systemmanager" to
                    "com.huawei.systemmanager.optimize.process.ProtectActivity",
            ),
        )
        name.contains("honor") -> Vendor(
            displayName = "Honor",
            slug = "honor",
            stepsRes = R.string.wizard_steps_honor,
            intentComponents = listOf(
                "com.hihonor.systemmanager" to
                    "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
                "com.hihonor.systemmanager" to
                    "com.hihonor.systemmanager.optimize.process.ProtectActivity",
            ),
        )
        name.contains("google") -> Vendor(
            displayName = "Google",
            slug = "google",
            stepsRes = R.string.wizard_steps_google,
            intentComponents = emptyList(),
        )
        else -> null
    }
}
