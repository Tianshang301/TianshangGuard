package com.tianshang.guard.core.monitor

data class RemoteConfig(
    val screenShareApps: Set<String> = emptySet(),
    val bankApps: Set<String> = emptySet()
)

class RemoteConfigProvider {

    var screenShareApps: Set<String> = setOf(
        "com.teamviewer.teamviewer.market",
        "com.anydesk.adcontrol",
        "com.microsoft.rdc.androidx",
        "com.logmein.golook",

        "com.screenovate.zapya",
        "com.apowersoft.mirrorphone",
        "com.airmore.manager",
        "com.remotepc.remote",
        "com.xtralogic.remoteclient"
    )
    var bankApps: Set<String> = setOf(
        "com.eg.android.AlipayGphone",
        "com.tencent.mm",
        "com.tencent.mtt",
        "com.android.bankabc",
        "com.chinamworld.bocmbci",
        "com.icbc",
        "com.cmbc.ccmbsv",
        "com.spdb.express",
        "com.cgbchina.bill",
        "com.cebbank.bill",
        "com.hxb.credit",
        "com.bankcomm.Bankcomm",
        "com.android.bankbee",
        "com.sina.weibo",
        "com.tencent.mobileqq"
    )
}
