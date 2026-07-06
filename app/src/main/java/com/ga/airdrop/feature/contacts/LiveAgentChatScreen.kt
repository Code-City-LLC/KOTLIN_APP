package com.ga.airdrop.feature.contacts

import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import com.ga.airdrop.R
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.AirdropType
import com.ga.airdrop.core.designsystem.theme.Spacing

/**
 * Live Agent Chat — Swift FigmaLiveAgentChatViewController
 * (FigmaRouteViewController.swift:942-1075): inner header titled
 * "Live Agent Chat" over a WKWebView that boots the Trengo chat widget
 * (key VEoeiGPVu2O9GGh) with the panel header hidden and the chat panel
 * auto-opened, based at https://airdropja.com.
 */
private val TRENGO_HTML = """
    <!DOCTYPE html>
    <html>
      <head>
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <script type="text/javascript">
          window.Trengo = window.Trengo || {};
          window.Trengo.key = "VEoeiGPVu2O9GGh";
          window.Trengo.panelHeader = false;
          (function (d, script) {
            script = d.createElement("script");
            script.type = "text/javascript";
            script.async = true;
            script.src = "https://static.widget.trengo.eu/embed.js";
            window.Trengo.on_ready = function () {
              window.Trengo.Api.Widget.open("chat");
            };
            d.getElementsByTagName("head")[0].appendChild(script);
          })(document);
        </script>
      </head>
      <body></body>
    </html>
""".trimIndent()

@Composable
fun LiveAgentChatScreen(onBack: () -> Unit) {
    val colors = AirdropTheme.colors
    Column(Modifier.fillMaxSize().background(colors.gray200)) {
        // Inner header — Swift makeInnerHeader: gray100, back chevron 24,
        // SubTitle1 centered title, 1dp iconShape bottom divider.
        Column(
            Modifier
                .fillMaxWidth()
                .background(colors.gray100)
                .windowInsetsPadding(WindowInsets.statusBars)
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            ) {
                // Swift FigmaRouteViewController.swift:1023-1028 — 40x40 back button
                // (24 chevron centered), leading inset 16 (was a bare 24 icon).
                Box(
                    Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 16.dp)
                        .size(40.dp)
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_small_arrow_down),
                        contentDescription = "Back",
                        colorFilter = ColorFilter.tint(colors.textDarkTitle),
                        modifier = Modifier
                            .size(24.dp)
                            .rotate(90f),
                    )
                }
                Text(
                    text = "Live Agent Chat",
                    style = AirdropType.subtitle1,
                    color = colors.textDarkTitle,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(colors.iconShape)
            )
        }

        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    webViewClient = WebViewClient()
                    loadDataWithBaseURL(
                        "https://airdropja.com",
                        TRENGO_HTML,
                        "text/html",
                        "utf-8",
                        null,
                    )
                }
            },
        )
    }
}
