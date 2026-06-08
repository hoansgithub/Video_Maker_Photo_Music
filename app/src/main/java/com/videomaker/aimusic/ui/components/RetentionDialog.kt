package com.videomaker.aimusic.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.videomaker.aimusic.R
import com.videomaker.aimusic.ui.theme.Neutral_N800
import com.videomaker.aimusic.ui.theme.Neutral_N900
import com.videomaker.aimusic.ui.theme.Primary
import com.videomaker.aimusic.ui.theme.TextOnPrimary
import com.videomaker.aimusic.ui.theme.TextPrimaryDark
import com.videomaker.aimusic.ui.theme.TextSecondaryDark

private val PillShape = RoundedCornerShape(999.dp)

@Composable
fun RetentionDialog(
    onClose: () -> Unit,
    onStay: () -> Unit
) {
    Dialog(
        onDismissRequest = onStay,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Neutral_N900, RoundedCornerShape(24.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.retention_dialog_title),
                color = TextPrimaryDark,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                text = stringResource(R.string.retention_dialog_subtitle),
                color = TextSecondaryDark,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 24.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onClose,
                    modifier = Modifier.weight(1f),
                    shape = PillShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Neutral_N800,
                        contentColor = TextPrimaryDark
                    )
                ) {
                    Text(
                        text = stringResource(R.string.retention_dialog_close),
                        fontWeight = FontWeight.Medium
                    )
                }

                Button(
                    onClick = onStay,
                    modifier = Modifier.weight(2f),
                    shape = PillShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Primary,
                        contentColor = TextOnPrimary
                    )
                ) {
                    Text(
                        text = stringResource(R.string.retention_dialog_stay),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
