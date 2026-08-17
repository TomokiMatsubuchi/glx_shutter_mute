package com.shuttermute

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ShutterMuteTheme { SettingsScreen() } }
    }
}

@Composable
private fun ShutterMuteTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = androidx.compose.material3.darkColorScheme()) {
        Surface(modifier = Modifier.fillMaxSize()) { content() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen() {
    val context = LocalContext.current
    var hasPermission by remember { mutableStateOf(ShutterSetting.hasPermission(context)) }
    var muteAllowed by remember { mutableStateOf(ShutterSetting.isMuteAllowed(context)) }
    var toast by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringRes(R.string.app_name)) }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "マナーモードでシャッター音を消す",
                    modifier = Modifier.weight(1f),
                    fontSize = 18.sp
                )
                Switch(
                    enabled = hasPermission,
                    checked = muteAllowed,
                    onCheckedChange = { newValue ->
                        val ok = ShutterSetting.setMuteAllowed(context, newValue)
                        if (ok) {
                            muteAllowed = newValue
                            toast = null
                        } else {
                            hasPermission = ShutterSetting.hasPermission(context)
                            toast = "変更できませんでした"
                        }
                    }
                )
            }

            Text(
                text = if (muteAllowed) stringRes(R.string.state_on) else stringRes(R.string.state_off),
                style = MaterialTheme.typography.titleMedium
            )

            toast?.let {
                Text(text = it, color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
            }

            if (!hasPermission) {
                PermissionHint(context = context)
            }
        }
    }
}

@Composable
private fun PermissionHint(context: Context) {
    Text(
        text = "権限が不足しています。下記を一度だけ adb で実行すると永続します。",
        style = MaterialTheme.typography.bodySmall
    )
    val command = "adb shell pm grant com.shuttermute android.permission.WRITE_SECURE_SETTINGS"
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) { append(command) }
        },
        style = MaterialTheme.typography.bodySmall
    )
    OutlinedButton(onClick = {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("adb", command))
        Toast.makeText(context, "コピーしました", Toast.LENGTH_SHORT).show()
    }) {
        Text("コマンドをコピー")
    }
}

@Composable
private fun stringRes(resId: Int): String =
    androidx.compose.ui.res.stringResource(resId)
