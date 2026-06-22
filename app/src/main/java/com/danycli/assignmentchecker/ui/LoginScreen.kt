package com.danycli.assignmentchecker.ui

import android.graphics.Bitmap
import android.net.http.SslError
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.toSize
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material.icons.filled.Close
import com.danycli.assignmentchecker.RegistrationHistoryStore
import com.danycli.assignmentchecker.BuildConfig
import com.danycli.assignmentchecker.CAPTCHA_RETRY_DELAY_MS
import com.danycli.assignmentchecker.MainViewModel
import com.danycli.assignmentchecker.R
import com.danycli.assignmentchecker.retryIo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.IOException

@Composable
fun AppSplashScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = CircleShape,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(200.dp),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.mipmap.ic_launcher_foreground),
                    contentDescription = "Assignly Logo",
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
fun LoginScreen(
    isLoading: Boolean,
    onOpenDisclaimer: () -> Unit,
    onLogin: (String, String) -> Unit
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var usernameFocused by remember { mutableStateOf(false) }
    var passwordFocused by remember { mutableStateOf(false) }
    val isDark = isSystemInDarkTheme()

    val context = LocalContext.current
    var savedRegistrations by remember {
        mutableStateOf(RegistrationHistoryStore.getSavedRegistrations(context))
    }
    var isDropdownExpanded by remember { mutableStateOf(false) }
    var textFieldSize by remember { mutableStateOf(androidx.compose.ui.geometry.Size.Zero) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = if (isDark) {
                        listOf(MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.surface)
                    } else {
                        listOf(Color.White, MaterialTheme.colorScheme.background.copy(alpha = 0.3f), MaterialTheme.colorScheme.background)
                    }
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(20.dp))

            // App Logo with subtle elevation and soft glow
            Surface(
                modifier = Modifier
                    .size(100.dp)
                    .shadow(
                        elevation = 12.dp,
                        shape = CircleShape,
                        ambientColor = MaterialTheme.colorScheme.primary,
                        spotColor = MaterialTheme.colorScheme.primary
                    ),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Image(
                        painter = painterResource(id = R.mipmap.ic_launcher_foreground),
                        contentDescription = "App logo",
                        modifier = Modifier.size(110.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "ASSIGNLY",
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 4.sp
            )

            Text(
                text = "Academic companion",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Minimalist Input Card
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                val filteredList = remember(username, savedRegistrations) {
                    if (username.isBlank()) {
                        savedRegistrations
                    } else {
                        savedRegistrations.filter { it.contains(username, ignoreCase = true) }
                    }
                }

                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = username,
                        onValueChange = { 
                            username = it
                            isDropdownExpanded = true
                        },
                        label = { Text("Registration Number", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                        placeholder = { Text("SP25-BCS-001", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { 
                                usernameFocused = it.isFocused 
                                if (it.isFocused) {
                                    isDropdownExpanded = true
                                }
                            }
                            .onGloballyPositioned { coordinates ->
                                textFieldSize = coordinates.size.toSize()
                            },
                        shape = RoundedCornerShape(16.dp),
                        enabled = !isLoading,
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                            focusedLabelColor = MaterialTheme.colorScheme.primary,
                            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            disabledBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f),
                            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            disabledTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            cursorColor = MaterialTheme.colorScheme.primary,
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            disabledContainerColor = MaterialTheme.colorScheme.surface,
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                        ),
                        leadingIcon = {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = null,
                                tint = if (usernameFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    )

                    if (filteredList.isNotEmpty() && usernameFocused) {
                        DropdownMenu(
                            expanded = isDropdownExpanded,
                            onDismissRequest = { isDropdownExpanded = false },
                            modifier = Modifier
                                .width(with(LocalDensity.current) { textFieldSize.width.toDp() })
                                .background(MaterialTheme.colorScheme.surface),
                            properties = PopupProperties(focusable = false)
                        ) {
                            filteredList.forEach { regNum ->
                                DropdownMenuItem(
                                    onClick = {
                                        username = regNum
                                        isDropdownExpanded = false
                                    },
                                    text = {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Default.Person,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Text(
                                                    text = regNum,
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                            IconButton(
                                                onClick = {
                                                    RegistrationHistoryStore.removeRegistration(context, regNum)
                                                    savedRegistrations = RegistrationHistoryStore.getSavedRegistrations(context)
                                                },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Close,
                                                    contentDescription = "Remove registration history",
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { passwordFocused = it.isFocused },
                    shape = RoundedCornerShape(16.dp),
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        autoCorrect = false
                    ),
                    trailingIcon = {
                        val image = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
                        IconButton(
                            onClick = { passwordVisible = !passwordVisible },
                            enabled = !isLoading
                        ) {
                            Icon(
                                imageVector = image,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    },
                    enabled = !isLoading,
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        disabledBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f),
                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        disabledTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        cursorColor = MaterialTheme.colorScheme.primary,
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        disabledContainerColor = MaterialTheme.colorScheme.surface,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    ),
                    leadingIcon = {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = null,
                            tint = if (passwordFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = { onLogin(username, password) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                    ),
                    enabled = !isLoading && username.isNotBlank() && password.isNotBlank()
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("VERIFYING...", fontWeight = FontWeight.Black, fontSize = 13.sp, color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Text(
                            "SIGN IN",
                            fontWeight = FontWeight.Black,
                            fontSize = 14.sp,
                            letterSpacing = 2.sp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            // Subtler format helper
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Use format: SP25-BCS-001",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            
            TextButton(onClick = onOpenDisclaimer) {
                Text(
                    "Security & Privacy Disclaimer",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptchaWebViewDialog(
    viewModel: MainViewModel,
    onDismiss: () -> Unit,
    onCaptchaSolved: () -> Unit
) {
    val context = LocalContext.current
    val showMessage = LocalShowMessage.current
    val portalBaseUrl = remember { viewModel.getPortalBaseUrl() }
    val loginUrl = remember { viewModel.getPortalLoginUrl() }
    val portalHost = remember(loginUrl) { runCatching { loginUrl.toHttpUrl().host }.getOrDefault("") }
    val loginScheme = remember(loginUrl) { runCatching { loginUrl.toHttpUrl().scheme }.getOrDefault("https") }
    var pageTitle by remember { mutableStateOf("Security Verification") }
    var isPageLoading by remember { mutableStateOf(true) }
    var challengeLooksSolved by remember { mutableStateOf(false) }
    var challengeEncountered by remember { mutableStateOf(false) }
    var clearanceCookieSeen by remember { mutableStateOf(false) }
    var hasAutoSubmitted by remember { mutableStateOf(false) }
    var noChallengeBypassReady by remember { mutableStateOf(false) }
    var isCompletingVerification by remember { mutableStateOf(false) }
    var currentUrl by remember { mutableStateOf(loginUrl) }
    var shouldRenderWebView by remember { mutableStateOf(false) }
    var pendingSslError by remember { mutableStateOf<SslError?>(null) }
    var pendingSslHandler by remember { mutableStateOf<SslErrorHandler?>(null) }
    val webViewRef = remember { mutableStateOf<WebView?>(null) }
    val webViewUa = remember {
        runCatching { WebSettings.getDefaultUserAgent(context) }
            .getOrDefault(BuildConfig.PORTAL_USER_AGENT)
    }

    fun isLikelyChallenge(url: String, title: String): Boolean {
        val normalizedUrl = url.lowercase()
        val normalizedTitle = title.lowercase()
        return normalizedUrl.contains("/cdn-cgi/") ||
            normalizedUrl.contains("challenge-platform") ||
            normalizedUrl.contains("captcha") ||
            normalizedUrl.contains("security") ||
            normalizedUrl.startsWith("chrome-error://") ||
            normalizedTitle.contains("your connection is not private") ||
            normalizedTitle.contains("privacy error") ||
            normalizedTitle.contains("security verification") ||
            normalizedTitle.contains("just a moment") ||
            normalizedTitle.contains("verify you are human")
    }

    fun sslWarningMessage(error: SslError?): String {
        val blockedUrl = error?.url?.takeIf { it.isNotBlank() } ?: currentUrl
        return "This network is showing a connection privacy warning for $blockedUrl. Continue only if you trust this network."
    }

    fun isPortalHostUrl(url: String): Boolean {
        if (portalHost.isBlank()) return false
        val candidateHost = runCatching { url.toHttpUrl().host.lowercase() }.getOrDefault("")
        if (candidateHost.isBlank()) return false
        val canonicalPortalHost = portalHost.lowercase()
        return candidateHost == canonicalPortalHost ||
            candidateHost.endsWith(".$canonicalPortalHost") ||
            canonicalPortalHost.endsWith(".$candidateHost")
    }

    fun injectCookiesFromCurrentSession(): Int {
        val manager = CookieManager.getInstance()
        var totalInjected = 0
        val targetUrls = linkedSetOf(
            portalBaseUrl,
            loginUrl,
            currentUrl,
            "$loginScheme://$portalHost"
        ).filter { it.isNotBlank() }
        targetUrls.forEach { url ->
            val cookieHeader = manager.getCookie(url)
            totalInjected += viewModel.injectCookiesFromWebView(cookieHeader, url)
        }
        return totalInjected
    }

    suspend fun isCaptchaStillRequiredBeforeWebView(): Boolean {
        return try {
            withTimeout(10_000) {
                withContext(Dispatchers.IO) {
                    retryIo(maxAttempts = 2, initialDelayMs = CAPTCHA_RETRY_DELAY_MS) {
                        viewModel.isSecurityVerificationStillRequired()
                    }
                }
            }
        } catch (e: TimeoutCancellationException) {
            true
        } catch (e: IOException) {
            true
        }
    }

    fun completeCaptchaFlow(message: String? = null, showToast: Boolean = true) {
        if (hasAutoSubmitted) return
        viewModel.setUserAgentForSession(webViewUa)
        CookieManager.getInstance().flush()
        val injected = injectCookiesFromCurrentSession()
        if (injected <= 0) {
            isCompletingVerification = false
            return
        }
        hasAutoSubmitted = true
        isCompletingVerification = true
        webViewRef.value?.stopLoading()
        webViewRef.value?.loadUrl("about:blank")
        if (showToast && !message.isNullOrBlank()) {
            showMessage(message)
        }
        onCaptchaSolved()
    }

    fun scheduleAutoContinueIfReady() {
        if (hasAutoSubmitted || !challengeEncountered || !clearanceCookieSeen || !challengeLooksSolved || !isPortalHostUrl(currentUrl)) {
            isCompletingVerification = false
            return
        }
        completeCaptchaFlow("Verification completed. Signing in...")
    }

    fun scheduleNoChallengeContinueIfReady() {
        if (hasAutoSubmitted || !noChallengeBypassReady || !isPortalHostUrl(currentUrl)) {
            isCompletingVerification = false
            return
        }
        completeCaptchaFlow(showToast = false)
    }

    fun dismissVerificationDialog() {
        pendingSslHandler?.cancel()
        pendingSslHandler = null
        pendingSslError = null
        onDismiss()
    }

    fun shouldFinishBeforePortalPaint(targetUrl: String): Boolean {
        if (hasAutoSubmitted || targetUrl.isBlank()) return false
        return challengeEncountered &&
            isPortalHostUrl(targetUrl) &&
            !isLikelyChallenge(targetUrl, "")
    }

    LaunchedEffect(Unit) {
        val captchaStillRequired = isCaptchaStillRequiredBeforeWebView()
        if (!captchaStillRequired) {
            onCaptchaSolved()
            return@LaunchedEffect
        }
        shouldRenderWebView = true
    }

    Dialog(onDismissRequest = ::dismissVerificationDialog) {
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 460.dp, max = 700.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                TopAppBar(
                    title = {
                        Text(
                            text = pageTitle.ifBlank { "Security Verification" },
                            maxLines = 1,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = ::dismissVerificationDialog) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Close verification",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    },
                    actions = {
                        TextButton(onClick = { webViewRef.value?.reload() }) {
                            Text("Reload", color = MaterialTheme.colorScheme.onPrimary)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary)
                )

                if (isPageLoading) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    if (shouldRenderWebView) {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { viewContext ->
                                WebView(viewContext).apply {
                                    webViewRef.value = this
                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = true
                                    @Suppress("DEPRECATION")
                                    settings.databaseEnabled = true
                                    settings.cacheMode = WebSettings.LOAD_DEFAULT
                                    settings.userAgentString = webViewUa
                                    settings.javaScriptCanOpenWindowsAutomatically = true
                                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                                    settings.loadsImagesAutomatically = true
                                    settings.mediaPlaybackRequiresUserGesture = false
                                    settings.builtInZoomControls = false
                                    settings.displayZoomControls = false
                                    CookieManager.getInstance().setAcceptCookie(true)
                                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                                    viewModel.setUserAgentForSession(webViewUa)

                                    webChromeClient = WebChromeClient()
                                    webViewClient = object : WebViewClient() {
                                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                            isPageLoading = true
                                            noChallengeBypassReady = false
                                            pendingSslHandler = null
                                            pendingSslError = null
                                            if (!url.isNullOrBlank()) {
                                                currentUrl = url
                                                val normalizedUrl = url.lowercase()
                                                if (normalizedUrl.contains("/cdn-cgi/") || normalizedUrl.contains("challenge-platform")) {
                                                    challengeEncountered = true
                                                }
                                                val cookieSnapshot = CookieManager.getInstance().getCookie(url)
                                                    ?: CookieManager.getInstance().getCookie(portalBaseUrl)
                                                val hasClearanceCookie = cookieSnapshot?.contains("cf_clearance=", ignoreCase = true) == true
                                                clearanceCookieSeen = clearanceCookieSeen || hasClearanceCookie
                                                if (shouldFinishBeforePortalPaint(url)) {
                                                    completeCaptchaFlow("Verification completed. Signing in...")
                                                }
                                            }
                                            super.onPageStarted(view, url, favicon)
                                        }

                                        override fun onPageFinished(view: WebView?, url: String?) {
                                            if (!url.isNullOrBlank()) {
                                                currentUrl = url
                                            }
                                            pageTitle = view?.title?.takeIf { it.isNotBlank() } ?: "Security Verification"
                                            isPageLoading = false
                                            val resolvedUrl = url.orEmpty()
                                            val resolvedTitle = view?.title.orEmpty()
                                            val cookieSnapshot = CookieManager.getInstance().getCookie(resolvedUrl)
                                                ?: CookieManager.getInstance().getCookie(portalBaseUrl)
                                            val hasClearanceCookie = cookieSnapshot?.contains("cf_clearance=", ignoreCase = true) == true
                                            val hasChallengeCookie = cookieSnapshot?.let { cookies ->
                                                cookies.contains("__cf_bm=", ignoreCase = true) ||
                                                    cookies.contains("cf_chl", ignoreCase = true)
                                            } == true
                                            val normalizedUrl = resolvedUrl.lowercase()
                                            val onChallengeEndpoint = normalizedUrl.contains("/cdn-cgi/") ||
                                                normalizedUrl.contains("challenge-platform")
                                            val likelyChallenge = isLikelyChallenge(resolvedUrl, resolvedTitle) || onChallengeEndpoint
                                            if (likelyChallenge || (hasChallengeCookie && !hasClearanceCookie)) {
                                                challengeEncountered = true
                                            }
                                            clearanceCookieSeen = clearanceCookieSeen || hasClearanceCookie
                                            val portalWithoutChallenge = isPortalHostUrl(resolvedUrl) &&
                                                !onChallengeEndpoint &&
                                                !likelyChallenge
                                            noChallengeBypassReady = !challengeEncountered && portalWithoutChallenge
                                            challengeLooksSolved = isPortalHostUrl(resolvedUrl) &&
                                                !onChallengeEndpoint &&
                                                !likelyChallenge &&
                                                challengeEncountered &&
                                                clearanceCookieSeen
                                            injectCookiesFromCurrentSession()
                                            scheduleAutoContinueIfReady()
                                            scheduleNoChallengeContinueIfReady()
                                            super.onPageFinished(view, url)
                                        }

                                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                            val targetUrl = request?.url?.toString().orEmpty()
                                            if ((request?.isForMainFrame != false) && shouldFinishBeforePortalPaint(targetUrl)) {
                                                completeCaptchaFlow("Verification completed. Signing in...")
                                                return true
                                            }
                                            return false
                                        }

                                        override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                                            if (handler == null) return
                                            isPageLoading = false
                                            isCompletingVerification = false
                                            challengeEncountered = true
                                            challengeLooksSolved = false
                                            noChallengeBypassReady = false
                                            val blockedUrl = error?.url
                                            if (!blockedUrl.isNullOrBlank()) {
                                                currentUrl = blockedUrl
                                            }
                                            pageTitle = "Connection warning"
                                            pendingSslHandler?.cancel()
                                            pendingSslError = error
                                            pendingSslHandler = handler
                                        }
                                    }
                                    loadUrl(loginUrl)
                                }
                            },
                            update = { webView ->
                                webViewRef.value = webView
                            }
                        )
                    } else {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Checking verification status...",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }

                    if (isCompletingVerification || hasAutoSubmitted) {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Verification completed. Finishing sign-in...",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (noChallengeBypassReady && !hasAutoSubmitted) {
                        OutlinedButton(
                            onClick = {
                                completeCaptchaFlow("Continuing sign-in...")
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("Continue")
                        }
                        Button(
                            onClick = ::dismissVerificationDialog,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("Cancel", color = MaterialTheme.colorScheme.onPrimary)
                        }
                    } else {
                        Button(
                            onClick = ::dismissVerificationDialog,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("Cancel", color = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                }

                if (noChallengeBypassReady && !hasAutoSubmitted) {
                    Text(
                        text = "No security check prompt detected. Sign-in continues automatically.",
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else if (!challengeLooksSolved) {
                    Text(
                        text = "Complete verification. Sign-in continues automatically.",
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else if (!hasAutoSubmitted) {
                    Text(
                        text = "Verification detected. Completing sign-in...",
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }

    pendingSslHandler?.let { sslHandler ->
        AlertDialog(
            onDismissRequest = {
                sslHandler.cancel()
                pendingSslHandler = null
                pendingSslError = null
            },
            title = { Text("Your connection is not private", color = MaterialTheme.colorScheme.primary) },
            text = {
                Text(
                    text = sslWarningMessage(pendingSslError),
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingSslHandler?.proceed()
                        pendingSslHandler = null
                        pendingSslError = null
                        isPageLoading = true
                    }
                ) {
                    Text("Continue", color = MaterialTheme.colorScheme.primary)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        pendingSslHandler?.cancel()
                        pendingSslHandler = null
                        pendingSslError = null
                    }
                ) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            pendingSslHandler?.cancel()
            webViewRef.value?.apply {
                stopLoading()
                clearHistory()
                webChromeClient = null
                webViewClient = WebViewClient()
                destroy()
            }
            webViewRef.value = null
            CookieManager.getInstance().flush()
        }
    }
}
