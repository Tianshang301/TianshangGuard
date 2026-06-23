package com.tianshang.guard.ui.onboarding

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tianshang.guard.ui.theme.DeepNavy
import com.tianshang.guard.ui.theme.GuardRed
import com.tianshang.guard.ui.theme.OnSurfaceDark
import com.tianshang.guard.ui.theme.OnSurfaceVariantDark
import com.tianshang.guard.ui.theme.SurfaceVariantDark
import kotlinx.coroutines.launch

data class OnboardingPage(val emoji: String, val title: String, val description: String)

private val pages = listOf(
    OnboardingPage("\uD83D\uDEE1\uFE0F", "\u6B22\u8FCE\u4F7F\u7528\u5929\u6B87\u00B7\u7834\u5984", "\u4E00\u6B3E\u5F00\u6E90 Android \u53CD\u8BC8\u5DE5\u5177\uFF0C\u591A\u5C42\u9632\u5FA1\u4FDD\u62A4\u60A8\u7684\u7F51\u7EDC\u5B89\u5168"),
    OnboardingPage("\uD83C\uDFAF", "DNS \u9632\u62A4", "\u62E6\u622A\u5DF2\u77E5\u94F1\u9C7C\u57DF\u540D\uFF0C\u68C0\u6D4B\u4EFF\u5192\u57DF\u540D\u548C\u540C\u5F62\u5B57\u7B26\u653B\u51FB"),
    OnboardingPage("\uD83D\uDEE1\uFE0F", "\u884C\u4E3A\u76D1\u63A7", "\u68C0\u6D4B\u5C4F\u5E55\u5171\u4EAB\u4E0E\u94F6\u884C\u5E94\u7528\u7EC4\u5408\uFF0C\u963B\u65AD\u793E\u4F1A\u5DE5\u7A0B\u5B66\u653B\u51FB")
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(onComplete: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize().background(DeepNavy)) {
        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f).fillMaxWidth()) { pageIndex ->
            val page = pages[pageIndex]
            Column(modifier = Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text(page.emoji, fontSize = 72.sp)
                Spacer(modifier = Modifier.height(32.dp))
                Text(page.title, style = MaterialTheme.typography.headlineMedium, color = OnSurfaceDark, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(16.dp))
                Text(page.description, style = MaterialTheme.typography.bodyLarge, color = OnSurfaceVariantDark, textAlign = TextAlign.Center)
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.Center) {
            repeat(pages.size) { index ->
                Box(modifier = Modifier.padding(4.dp).size(if (index == pagerState.currentPage) 10.dp else 8.dp).clip(CircleShape).background(if (index == pagerState.currentPage) GuardRed else SurfaceVariantDark))
            }
        }
        Button(
            onClick = {
                if (pagerState.currentPage < pages.size - 1) {
                    scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                } else {
                    onComplete()
                }
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 24.dp).height(48.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = GuardRed)
        ) { Text(if (pagerState.currentPage < pages.size - 1) "\u4E0B\u4E00\u6B65" else "\u5F00\u59CB\u4F7F\u7528", fontWeight = FontWeight.Bold) }
        Spacer(modifier = Modifier.height(8.dp))
    }
}
