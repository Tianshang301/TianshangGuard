package com.tianshang.guard.di

import com.tianshang.guard.core.alert.AlertEngine
import com.tianshang.guard.core.alert.TieredAlertEngine
import com.tianshang.guard.core.calibration.ThresholdCalibrator
import com.tianshang.guard.core.dns.DnsEngine
import com.tianshang.guard.core.dns.DohClient
import com.tianshang.guard.core.dns.HomographDetector
import com.tianshang.guard.core.dns.LocalDnsEngine
import com.tianshang.guard.core.dns.Web3DomainDetector
import com.tianshang.guard.core.feedback.FeedbackEngine
import com.tianshang.guard.core.ml.BpeTokenizer
import com.tianshang.guard.core.ml.MlEngine
import com.tianshang.guard.core.ml.MlEngineWithFallback
import com.tianshang.guard.core.ml.OnnxMlEngine
import com.tianshang.guard.core.ml.RuleBasedEngine
import com.tianshang.guard.core.monitor.RemoteConfigProvider
import com.tianshang.guard.core.monitor.ScreenShareMonitor
import com.tianshang.guard.core.quish.QrCodeDecoder
import com.tianshang.guard.core.quish.QuishGuardEngine
import com.tianshang.guard.core.retrieval.KnowledgeBase
import com.tianshang.guard.core.rl.FeatureBasedPredictor
import com.tianshang.guard.core.rl.FeatureExtractor
import com.tianshang.guard.core.rl.FeatureStore
import com.tianshang.guard.core.telemetry.PerformanceTracer
import com.tianshang.guard.core.update.RuleUpdateInteractor
import com.tianshang.guard.core.update.SignatureVerifier
import com.tianshang.guard.data.local.GuardPreferences
import com.tianshang.guard.data.local.security.EncryptedDatabaseProvider
import com.tianshang.guard.data.remote.GithubRulesApi
import com.tianshang.guard.data.repository.AlertRepository
import com.tianshang.guard.data.repository.RuleRepository
import com.tianshang.guard.domain.AnalyzeSmsUseCase
import com.tianshang.guard.domain.InterceptQrUseCase
import com.tianshang.guard.domain.CheckDomainRiskUseCase
import com.tianshang.guard.ui.main.MainViewModel
import com.tianshang.guard.ui.qr.QrPreviewViewModel
import com.tianshang.guard.ui.settings.SettingsViewModel
import com.tianshang.guard.ui.sms.SmsViewModel
import com.tianshang.guard.ui.stats.StatsViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val appModule = module {

    // ── Data Layer ──────────────────────────────────────────
    single { EncryptedDatabaseProvider(androidContext()) }
    single { get<EncryptedDatabaseProvider>().createDatabase() }
    single { get<com.tianshang.guard.data.local.database.GuardDatabase>().domainDao() }
    single { get<com.tianshang.guard.data.local.database.GuardDatabase>().alertDao() }
    single { get<com.tianshang.guard.data.local.database.GuardDatabase>().feedbackDao() }

    single { GuardPreferences.create(androidContext()) }
    single { RuleRepository(get()) }
    single { AlertRepository(get()) }

    // ── Networking Layer ────────────────────────────────────
    single {
        okhttp3.OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }
    single {
        retrofit2.Retrofit.Builder()
            .baseUrl("https://raw.githubusercontent.com/tianshang-guard/rules/main/")
            .client(get())
            .addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create())
            .build()
    }
    single { get<retrofit2.Retrofit>().create(GithubRulesApi::class.java) }

    // ── DoH (DNS over HTTPS) Client ────────────────────────
    single { DohClient(get()) }

    // ── Core Engine Layer ───────────────────────────────────
    single<AlertEngine> { TieredAlertEngine(androidContext(), get(), get()) }
    single { HomographDetector }
    single { RemoteConfigProvider() }
    single { PerformanceTracer }

    single<DnsEngine> { LocalDnsEngine(get(), get(), get(), get(), get()) }
    single { ScreenShareMonitor(androidContext(), get(), get()) }

    // ── RL Engine Layer ─────────────────────────────────────
    single { FeatureExtractor() }
    single { FeatureStore(get()) }
    single { FeatureBasedPredictor(get()) }

    single { KnowledgeBase(androidContext()) }
    single { FeedbackEngine(get(), get(), get(), get()) }
    single { ThresholdCalibrator(androidContext(), get()) }
    single {
        BpeTokenizer(androidContext()).also { it.load() }
    }
    single { OnnxMlEngine(bpeTokenizer = get()) }
    single { RuleBasedEngine(androidContext()) }
    single<MlEngine> { MlEngineWithFallback(get(), get(), get(), get(), get(), get(), get()) }

    single { AnalyzeSmsUseCase(get(), get()) }

    // ── v1.5.0: QuishGuard QR Scanner ──────────────────────
    single { QrCodeDecoder() }
    single { CheckDomainRiskUseCase(get()) }
    single { QuishGuardEngine(get(), get(), get()) }
    single { InterceptQrUseCase(get()) }

    // ── v1.5.0: Web3Guard Domain Detector ──────────────────
    single { Web3DomainDetector() }

    // ── Rule Update ──────────────────────────────────────────
    single { SignatureVerifier() }
    single { RuleUpdateInteractor(get(), get(), get(), get()) }

    // ── ViewModels ──────────────────────────────────────────
    viewModel { MainViewModel(get(), get(), get(), get()) }
    viewModel { QrPreviewViewModel(get(), get()) }
    viewModel { StatsViewModel(get()) }
    viewModel { SettingsViewModel(get(), get(), get(), get(), get(), get()) }
    viewModel { SmsViewModel(get(), get()) }
}
