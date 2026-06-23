package com.tianshang.guard.core.optimizer

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

enum class PhoneBrand(val label: String) {
    HUAWEI("华为 / 荣耀"),
    XIAOMI("小米 / Redmi"),
    OPPO("OPPO / OnePlus"),
    VIVO("vivo / iQOO"),
    MEIZU("魅族"),
    SAMSUNG("三星"),
    GOOGLE("原生 Android"),
    OTHER("其他设备");

    companion object {
        fun detect(): PhoneBrand {
            val manufacturer = Build.MANUFACTURER.lowercase()
            return when {
                manufacturer.contains("huawei") || manufacturer.contains("honor") -> HUAWEI
                manufacturer.contains("xiaomi") || manufacturer.contains("redmi") -> XIAOMI
                manufacturer.contains("oppo") || manufacturer.contains("oneplus") -> OPPO
                manufacturer.contains("vivo") || manufacturer.contains("iqoo") -> VIVO
                manufacturer.contains("meizu") -> MEIZU
                manufacturer.contains("samsung") -> SAMSUNG
                manufacturer.contains("google") -> GOOGLE
                else -> OTHER
            }
        }
    }
}

object BatteryOptimizer {

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun openBatterySettings(context: Context) {
        val pkg = context.packageName
        val brand = PhoneBrand.detect()
        val intent = buildBrandIntent(brand, pkg)
            ?: buildGenericIntent(pkg)

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            context.startActivity(
                buildGenericIntent(pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    fun openAutoStartSettings(context: Context) {
        val pkg = context.packageName
        val brand = PhoneBrand.detect()
        val intent = when (brand) {
            PhoneBrand.XIAOMI -> Intent().apply {
                `package` = "com.miui.securitycenter"
                action = "miui.intent.action.AUTO_START_MANAGEMENT"
            }
            PhoneBrand.HUAWEI -> Intent().apply {
                `package` = "com.huawei.systemmanager"
                action = "huawei.intent.action.HSM_PROTECTED_APPS"
                putExtra("package_name", pkg)
            }
            PhoneBrand.OPPO -> Intent().apply {
                `package` = "com.coloros.safecenter"
                action = "coloros.safecenter.action.START_APP_PERMISSION"
            }
            PhoneBrand.VIVO -> Intent().apply {
                `package` = "com.iqoo.secure"
                action = "com.iqoo.secure.permission.PermissionManagerActivity"
            }
            PhoneBrand.MEIZU -> Intent().apply {
                `package` = "com.meizu.safe"
                action = "com.meizu.safe.permission.SmartPermissionActivity"
            }
            else -> Settings.ACTION_APPLICATION_DETAILS_SETTINGS.let { action ->
                Intent(action).apply { data = Uri.parse("package:$pkg") }
            }
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:$pkg")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }
    }

    fun getBrandName(): String = PhoneBrand.detect().label

    private fun buildBrandIntent(brand: PhoneBrand, pkg: String): Intent? {
        return when (brand) {
            PhoneBrand.HUAWEI -> Intent("huawei.intent.action.HSM_PROTECTED_APPS").apply {
                `package` = "com.huawei.systemmanager"
                putExtra("package_name", pkg)
                putExtra("pkg_name", pkg)
            }
            PhoneBrand.XIAOMI -> Intent("miui.intent.action.APP_PERM_EDITOR").apply {
                `package` = "com.miui.securitycenter"
                putExtra("extra_pkgname", pkg)
            }
            PhoneBrand.OPPO -> Intent("com.coloros.safecenter.action.START_APP_PERMISSION").apply {
                `package` = "com.coloros.safecenter"
                putExtra("package_name", pkg)
            }
            PhoneBrand.VIVO -> Intent("com.iqoo.secure.action.ACTION_SETTING_BG_OPT").apply {
                `package` = "com.iqoo.secure"
                putExtra("package_name", pkg)
                putExtra("pkg_name", pkg)
            }
            PhoneBrand.MEIZU -> Intent("com.meizu.safe.action.SETTINGS_BG_OPT").apply {
                `package` = "com.meizu.safe"
                putExtra("packageName", pkg)
            }
            PhoneBrand.SAMSUNG -> Intent("com.samsung.android.sm.ACTION_BATTERY_SETTINGS").apply {
                `package` = "com.samsung.android.lool"
            }
            PhoneBrand.GOOGLE, PhoneBrand.OTHER -> null
        }
    }

    private fun buildGenericIntent(pkg: String): Intent {
        return Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:$pkg")
        }
    }
}
