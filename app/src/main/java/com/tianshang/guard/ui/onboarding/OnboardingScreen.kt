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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tianshang.guard.R
import com.tianshang.guard.ui.theme.DeepNavy
import com.tianshang.guard.ui.theme.GuardRed
import com.tianshang.guard.ui.theme.OnSurfaceDark
import com.tianshang.guard.ui.theme.OnSurfaceVariantDark
import com.tianshang.guard.ui.theme.SurfaceVariantDark
import kotlinx.coroutines.launch

data class OnboardingPage(val emoji: String, val titleResId: Int, val descriptionResId: Int)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(onComplete: () -> Unit) {
    val pages = listOf(
        OnboardingPage("\uD83D\uDEE1\uFE0F", R.string.onboarding_welcome_title, R.string.onboarding_welcome_desc),
        OnboardingPage("\uD83C\uDFAF", R.string.onboarding_dns_title, R.string.onboarding_dns_desc),
        OnboardingPage("\uD83D\uDEE1\uFE0F", R.string.onboarding_behavior_title, R.string.onboarding_behavior_desc)
    )
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize().background(DeepNavy)) {
        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f).fillMaxWidth()) { pageIndex ->
            val page = pages[pageIndex]
            Column(modifier = Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text(page.emoji, fontSize = 72.sp)
                Spacer(modifier = Modifier.height(32.dp))
                Text(stringResource(page.titleResId), style = MaterialTheme.typography.headlineMedium, color = OnSurfaceDark, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(16.dp))
                Text(stringResource(page.descriptionResId), style = MaterialTheme.typography.bodyLarge, color = OnSurfaceVariantDark, textAlign = TextAlign.Center)
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
        ) { Text(if (pagerState.currentPage < pages.size - 1) stringResource(R.string.onboarding_button_next) else stringResource(R.string.onboarding_button_start), fontWeight = FontWeight.Bold) }
        Spacer(modifier = Modifier.height(8.dp))
    }
}
