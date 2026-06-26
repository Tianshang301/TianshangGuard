package com.tianshang.guard.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tianshang.guard.R
import com.tianshang.guard.ui.theme.OnSurfaceVariantDark

@Composable
fun DisclaimerText(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("\u26A0\uFE0F", fontSize = 12.sp)
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = stringResource(R.string.model_disclaimer),
            style = MaterialTheme.typography.labelSmall,
            color = OnSurfaceVariantDark
        )
    }
}
