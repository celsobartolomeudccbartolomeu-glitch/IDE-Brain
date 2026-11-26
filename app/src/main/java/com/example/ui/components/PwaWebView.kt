package com.example.ui.components

import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import java.io.ByteArrayInputStream

private const val PWA_HOST = "brain-ide.local"
private const val PWA_ENTRY_URL = "https://$PWA_HOST/index.html"

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PwaWebView(
  modifier: Modifier = Modifier,
  onStatusMessage: (String) -> Unit,
) {
  val context = LocalContext.current
  var pendingFileCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
  var pendingExportContent by remember { mutableStateOf<String?>(null) }

  val importFileLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
      pendingFileCallback?.onReceiveValue(if (uri != null) arrayOf(uri) else emptyArray())
      pendingFileCallback = null
    }

  val exportFileLauncher =
    rememberLauncherForActivityResult(
      ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
      val content = pendingExportContent
      pendingExportContent = null
      if (uri != null && content != null) {
        runCatching {
          context.contentResolver.openOutputStream(uri)?.use { stream ->
            stream.write(content.toByteArray(Charsets.UTF_8))
            stream.flush()
          }
        }.onSuccess {
          onStatusMessage("Ficheiro .brain exportado.")
        }.onFailure {
          onStatusMessage("Erro ao exportar ficheiro.")
        }
      }
    }

  val webView = remember {
    WebView(context).apply {
      settings.apply {
        javaScriptEnabled = true
        domStorageEnabled = true
        databaseEnabled = true
        allowFileAccess = false
        allowContentAccess = false
        cacheMode = WebSettings.LOAD_DEFAULT
      }

      addJavascriptInterface(
        object {
          @JavascriptInterface
          fun exportBrainFile(fileName: String, content: String) {
            post {
              pendingExportContent = content
              exportFileLauncher.launch(fileName)
            }
          }
        },
        "AndroidBridge",
      )

      webViewClient =
        object : WebViewClient() {
          override fun shouldOverrideUrlLoading(
            view: WebView?,
            request: WebResourceRequest?,
          ): Boolean {
            return request?.url?.host != PWA_HOST
          }

          override fun shouldInterceptRequest(
            view: WebView?,
            request: WebResourceRequest?,
          ): WebResourceResponse? {
            val url = request?.url ?: return null
            if (url.host != PWA_HOST) return null
            val rawPath = url.path?.trimStart('/').orEmpty().ifEmpty { "index.html" }
            if (rawPath.contains("..")) return null
            return runCatching {
              val stream = context.assets.open("pwa/$rawPath")
              val mime = mimeTypeFor(rawPath)
              WebResourceResponse(mime, "UTF-8", stream)
            }.getOrElse {
              WebResourceResponse(
                "text/plain",
                "UTF-8",
                404,
                "Not Found",
                emptyMap(),
                ByteArrayInputStream(ByteArray(0)),
              )
            }
          }
        }

      webChromeClient =
        object : WebChromeClient() {
          override fun onShowFileChooser(
            webView: WebView?,
            filePathCallback: ValueCallback<Array<Uri>>?,
            fileChooserParams: FileChooserParams?,
          ): Boolean {
            pendingFileCallback?.onReceiveValue(null)
            pendingFileCallback = filePathCallback
            importFileLauncher.launch(arrayOf("*/*"))
            return true
          }
        }

      loadUrl(PWA_ENTRY_URL)
    }
  }

  DisposableEffect(Unit) {
    onDispose {
      pendingFileCallback?.onReceiveValue(null)
      webView.destroy()
    }
  }

  AndroidView(
    factory = { webView },
    modifier = modifier.fillMaxSize(),
  )
}

private fun mimeTypeFor(path: String): String {
  return when {
    path.endsWith(".html") -> "text/html"
    path.endsWith(".js") -> "application/javascript"
    path.endsWith(".css") -> "text/css"
    path.endsWith(".webmanifest") -> "application/manifest+json"
    path.endsWith(".svg") -> "image/svg+xml"
    else -> "application/octet-stream"
  }
}
