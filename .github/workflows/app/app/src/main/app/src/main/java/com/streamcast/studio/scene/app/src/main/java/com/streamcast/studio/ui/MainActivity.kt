package com.streamcast.studio.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.facebook.AccessToken
import com.facebook.CallbackManager
import com.facebook.FacebookCallback
import com.facebook.FacebookException
import com.facebook.FacebookSdk
import com.facebook.login.LoginManager
import com.facebook.login.LoginResult
import com.pedro.library.view.OpenGlView
import com.streamcast.studio.scene.SceneLayout
import com.streamcast.studio.service.StreamBroadcastService
import com.streamcast.studio.stream.FacebookLiveApi
import com.streamcast.studio.stream.LiveStreamer
import com.streamcast.studio.ui.theme.StreamCastTheme

class MainActivity : ComponentActivity() {

    companion object {
        private const val FB_PREFS = "fb_settings"
        private const val KEY_FB_APP_ID = "fb_app_id"
        private const val KEY_FB_TOKEN = "fb_client_token"
        private const val BREAKING = "ব্রেকিং নিউজ: নিউজ নেস্ট লাইভ সম্প্রচার চলছে..."
    }

    private lateinit var callbackManager: CallbackManager
    private lateinit var streamer: LiveStreamer

    private var isFacebookLoggedIn by mutableStateOf(false)
    private var isLive by mutableStateOf(false)
    private var statusText by mutableStateOf("")
    private var streamPlatform by mutableStateOf("Facebook")
    private var useGallery by mutableStateOf(false)
    private var useStreamKey by mutableStateOf(true)
    private var caption by mutableStateOf("")
    private var streamKey by mutableStateOf("")
    private var selectedVideoUri by mutableStateOf<Uri?>(null)
    private var pages by mutableStateOf<List<FacebookLiveApi.Page>>(emptyList())
    private var selectedPage by mutableStateOf<FacebookLiveApi.Page?>(null)
    private var liveVideoId: String? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val cameraOk = permissions[Manifest.permission.CAMERA] != false
        val micOk = permissions[Manifest.permission.RECORD_AUDIO] != false
        if (!cameraOk || !micOk) {
            Toast.makeText(this, "ক্যামেরা ও মাইক্রোফোন পারমিশন প্রয়োজন!", Toast.LENGTH_LONG).show()
        } else {
            streamer.refreshPreview()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        streamer = LiveStreamer(this, object : LiveStreamer.Listener {
            override fun onStatus(message: String) {
                statusText = message
            }

            override fun onStopped() {
                if (isLive) {
                    isLive = false
                    finishFacebookLive()
                    stopService(Intent(this@MainActivity, StreamBroadcastService::class.java))
                }
            }
        })

        checkPermissions()

        val saved = getSharedPreferences(FB_PREFS, Context.MODE_PRIVATE)
        applyFacebookSettings(
            saved.getString(KEY_FB_APP_ID, "") ?: "",
            saved.getString(KEY_FB_TOKEN, "") ?: ""
        )
        isFacebookLoggedIn = try {
            AccessToken.getCurrentAccessToken()?.isExpired == false
        } catch (e: Exception) {
            false
        }

        callbackManager = CallbackManager.Factory.create()
        LoginManager.getInstance().registerCallback(
            callbackManager,
            object : FacebookCallback<LoginResult> {
                override fun onSuccess(result: LoginResult) {
                    isFacebookLoggedIn = true
                    Toast.makeText(this@MainActivity, "ফেসবুক লগইন সফল!", Toast.LENGTH_SHORT).show()
                    loadFacebookPages()
                }

                override fun onCancel() {}

                override fun onError(error: FacebookException) {
                    Toast.makeText(this@MainActivity, "ত্রুটি: ${error.message}", Toast.LENGTH_SHORT).show()
                }
            }
        )

        setContent {
            StreamCastTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val fbPrefs = getSharedPreferences(FB_PREFS, Context.MODE_PRIVATE)
                    var showFbSettings by remember { mutableStateOf(false) }
                    var fbAppId by remember { mutableStateOf(fbPrefs.getString(KEY_FB_APP_ID, "") ?: "") }
                    var fbToken by remember { mutableStateOf(fbPrefs.getString(KEY_FB_TOKEN, "") ?: "") }

                    val videoPickerLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.GetContent()
                    ) { uri: Uri? ->
                        uri?.let {
                            selectedVideoUri = it
                            Toast.makeText(this@MainActivity, "ভিডিও সিলেক্ট করা হয়েছে", Toast.LENGTH_SHORT).show()
                        }
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                        if (useGallery) {
                            SceneLayout(
                                videoUri = selectedVideoUri,
                                tickerText = BREAKING,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            AndroidView(
                                factory = { ctx -> OpenGlView(ctx).also { streamer.attachPreview(it) } },
                                modifier = Modifier.fillMaxSize()
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.TopCenter)
                                    .background(Color.Red.copy(alpha = 0.8f))
                                    .padding(8.dp)
                            ) {
                                Text(text = BREAKING, color = Color.White, fontSize = 16.sp, maxLines = 1)
                            }
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 400.dp)
                                .align(Alignment.BottomCenter)
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ModeButton("ফোন ক্যামেরা", !useGallery, Color(0xFF00897B), !isLive, Modifier.weight(1f)) {
                                    useGallery = false
                                }
                                ModeButton("গ্যালারি ভিডিও (প্রিভিউ)", useGallery, Color(0xFF1976D2), !isLive, Modifier.weight(1f)) {
                                    useGallery = true
                                }
                            }

                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ModeButton("ফেসবুক", streamPlatform == "Facebook", Color(0xFF1877F2), !isLive, Modifier.weight(1f)) {
                                    streamPlatform = "Facebook"
                                }
                                ModeButton("ইউটিউব", streamPlatform == "YouTube", Color(0xFFFF0000), !isLive, Modifier.weight(1f)) {
                                    streamPlatform = "YouTube"
                                }
                            }

                            if (streamPlatform == "Facebook") {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    ModeButton("Stream Key দিয়ে", useStreamKey, Color(0xFF1877F2), !isLive, Modifier.weight(1f)) {
                                        useStreamKey = true
                                    }
                                    ModeButton("ফেসবুক লগইন দিয়ে", !useStreamKey, Color(0xFF1877F2), !isLive, Modifier.weight(1f)) {
                                        useStreamKey = false
                                    }
                                }

                                if (!useStreamKey) {
                                    Button(
                                        onClick = {
                                            if (!isFacebookLoggedIn) {
                                                LoginManager.getInstance().logInWithReadPermissions(
                                                    this@MainActivity,
                                                    listOf("pages_show_list", "pages_read_engagement", "pages_manage_posts", "publish_video")
                                                )
                                            } else {
                                                LoginManager.getInstance().logOut()
                                                isFacebookLoggedIn = false
                                                pages = emptyList()
                                                selectedPage = null
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (isFacebookLoggedIn) Color.Gray else Color(0xFF1877F2)
                                        ),
                                        modifier = Modifier.fillMaxWidth().height(40.dp)
                                    ) {
                                        Text(if (isFacebookLoggedIn) "ফেসবুক লগআউট" else "ফেসবুক লগইন", color = Color.White)
                                    }

                                    if (isFacebookLoggedIn) {
                                        Button(
                                            onClick = { loadFacebookPages() },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
                                            modifier = Modifier.fillMaxWidth().height(40.dp)
                                        ) {
                                            Text("আমার পেজ লোড করুন", color = Color.White)
                                        }
                                        pages.forEach { page ->
                                            Button(
                                                onClick = { selectedPage = page },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = if (selectedPage?.id == page.id) Color(0xFF00C853) else Color.DarkGray
                                                ),
                                                modifier = Modifier.fillMaxWidth().height(40.dp)
                                            ) {
                                                Text(page.name, color = Color.White)
                                            }
                                        }
                                    }
                                }

                                Button(
                                    onClick = { showFbSettings = !showFbSettings },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
                                    modifier = Modifier.fillMaxWidth().height(40.dp)
                                ) {
                                    Text(
                                        if (showFbSettings) "ফেসবুক সেটিংস বন্ধ করুন" else "ফেসবুক সেটিংস (App ID / Token)",
                                        color = Color.White
                                    )
                                }

                                if (showFbSettings) {
                                    OutlinedTextField(
                                        value = fbAppId,
                                        onValueChange = { fbAppId = it },
                                        label = { Text("Facebook App ID") },
                                        singleLine = true,
                                        colors = fieldColors(),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    OutlinedTextField(
                                        value = fbToken,
                                        onValueChange = { fbToken = it },
                                        label = { Text("Facebook Client Token") },
                                        singleLine = true,
                                        colors = fieldColors(),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Button(
                                        onClick = {
                                            fbPrefs.edit()
                                                .putString(KEY_FB_APP_ID, fbAppId.trim())
                                                .putString(KEY_FB_TOKEN, fbToken.trim())
                                                .apply()
                                            applyFacebookSettings(fbAppId, fbToken)
                                            Toast.makeText(this@MainActivity, "সেভ হয়েছে", Toast.LENGTH_SHORT).show()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00C853)),
                                        modifier = Modifier.fillMaxWidth().height(40.dp)
                                    ) {
                                        Text("সেভ করুন", color = Color.White)
                                    }
                                }
                            }

                            if (streamPlatform == "YouTube" || useStreamKey) {
                                OutlinedTextField(
                                    value = streamKey,
                                    onValueChange = { streamKey = it },
                                    label = {
                                        Text(if (streamPlatform == "Facebook") "ফেসবুক Stream Key" else "ইউটিউব Stream Key")
                                    },
                                    singleLine = true,
                                    colors = fieldColors(),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            if (useGallery) {
                                Button(
                                    onClick = { videoPickerLauncher.launch("video/*") },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2)),
                                    modifier = Modifier.fillMaxWidth().height(40.dp)
                                ) {
                                    Text("ফাইল ম্যানেজার থেকে ভিডিও সিলেক্ট করুন", color = Color.White)
                                }
                            }

                            OutlinedTextField(
                                value = caption,
                                onValueChange = { caption = it },
                                label = { Text("লাইভ ক্যাপশন / টাইটেল লিখুন...") },
                                colors = fieldColors(),
                                modifier = Modifier.fillMaxWidth()
                            )

                            if (statusText.isNotBlank()) {
                                Text(statusText, color = Color.White, fontSize = 14.sp)
                            }

                            Button(
                                onClick = { if (isLive) stopLive() else startLive() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isLive) Color.Red else Color(0xFF00C853)
                                ),
                                modifier = Modifier.fillMaxWidth().height(45.dp)
                            ) {
                                Text(
                                    text = if (isLive) "লাইভ বন্ধ করুন" else "$streamPlatform লাইভ শুরু করুন",
                                    color = Color.White,
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun ModeButton(
        text: String,
        selected: Boolean,
        activeColor: Color,
        enabled: Boolean,
        modifier: Modifier,
        onClick: () -> Unit
    ) {
        Button(
            onClick = onClick,
            enabled = enabled,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (selected) activeColor else Color.DarkGray,
                disabledContainerColor = if (selected) activeColor.copy(alpha = 0.6f) else Color.DarkGray.copy(alpha = 0.6f)
            ),
            modifier = modifier.height(40.dp)
        ) {
            Text(text, color = Color.White, fontSize = 12.sp)
        }
    }

    @Composable
    private fun fieldColors() = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = Color.Black.copy(alpha = 0.6f),
        unfocusedContainerColor = Color.Black.copy(alpha = 0.4f),
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White,
        focusedLabelColor = Color.White,
        unfocusedLabelColor = Color.LightGray,
        cursorColor = Color.White
    )

    override fun onResume() {
        super.onResume()
        streamer.refreshPreview()
    }

    override fun onDestroy() {
        streamer.release()
        super.onDestroy()
    }

    @Deprecated("Needed by the Facebook SDK callback manager")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        callbackManager.onActivityResult(requestCode, resultCode, data)
    }

    private fun applyFacebookSettings(appId: String, clientToken: String) {
        try {
            if (appId.isNotBlank()) FacebookSdk.setApplicationId(appId.trim())
            if (clientToken.isNotBlank()) FacebookSdk.setClientToken(clientToken.trim())
        } catch (e: Exception) {
            Toast.makeText(this, "ফেসবুক সেটিংস প্রয়োগ হয়নি: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun loadFacebookPages() {
        FacebookLiveApi.loadPages { list, error ->
            if (list == null) {
                Toast.makeText(this, error ?: "পেজ লোড করা যায়নি", Toast.LENGTH_LONG).show()
            } else {
                pages = list
                selectedPage = list.firstOrNull()
                if (list.isEmpty()) {
                    Toast.makeText(this, "এই অ্যাকাউন্টে কোনো পেজ পাওয়া যায়নি", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun neededPermissions(): List<String> {
        val permissions = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        return permissions
    }

    private fun checkPermissions() {
        val missing = neededPermissions().filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            requestPermissionLauncher.launch(missing.toTypedArray())
        }
    }

    private fun startLive() {
        if (useGallery) {
            Toast.makeText(
                this,
                "গ্যালারি ভিডিও থেকে সরাসরি স্ট্রিমিং এই ভার্সনে নেই। ফোন ক্যামেরা মোড বেছে নিন।",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val key = streamKey.trim()
        when {
            streamPlatform == "YouTube" -> {
                if (key.isBlank()) toast("ইউটিউব Stream Key দিন")
                else beginStream("rtmp://a.rtmp.youtube.com/live2/$key")
            }
            useStreamKey -> {
                if (key.isBlank()) toast("ফেসবুক Stream Key দিন")
                else beginStream("rtmps://live-api-s.facebook.com:443/rtmp/$key")
            }
            else -> {
                val page = selectedPage
                if (!isFacebookLoggedIn || page == null) {
                    toast("ফেসবুক লগইন করে একটি পেজ বেছে নিন")
                } else {
                    statusText = "ফেসবুকে লাইভ তৈরি হচ্ছে..."
                    FacebookLiveApi.createLive(page, caption.ifBlank { "NewsNestBD Live" }) { info, error ->
                        if (info == null) {
                            statusText = ""
                            toast(error ?: "লাইভ তৈরি করা যায়নি")
                        } else {
                            liveVideoId = info.id
                            beginStream(info.streamUrl)
                        }
                    }
                }
            }
        }
    }

    private fun beginStream(url: String) {
        val hasCamMic = listOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO).all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
        if (!hasCamMic) {
            toast("ক্যামেরা ও মাইক্রোফোন পারমিশন দিন")
            checkPermissions()
            finishFacebookLive()
            return
        }

        val intent = Intent(this, StreamBroadcastService::class.java).apply {
            action = StreamBroadcastService.ACTION_START_STREAM
            putExtra("EXTRA_PLATFORM", streamPlatform)
            putExtra("EXTRA_CAPTION", caption)
        }
        try {
            ContextCompat.startForegroundService(this, intent)
        } catch (e: Exception) {
            toast("সার্ভিস চালু করা যায়নি: ${e.message}")
            finishFacebookLive()
            return
        }

        if (streamer.start(url)) {
            isLive = true
            statusText = "সংযোগ হচ্ছে..."
        } else {
            stopService(Intent(this, StreamBroadcastService::class.java))
            toast("স্ট্রিম শুরু করা যায়নি (ক্যামেরা/মাইক চালু হয়নি)")
            finishFacebookLive()
        }
    }

    private fun stopLive() {
        streamer.stop()
        isLive = false
        statusText = "লাইভ বন্ধ করা হয়েছে"
        finishFacebookLive()
        stopService(Intent(this, StreamBroadcastService::class.java))
    }

    private fun finishFacebookLive() {
        val id = liveVideoId
        val page = selectedPage
        if (id != null && page != null) {
            FacebookLiveApi.endLive(page, id)
        }
        liveVideoId = null
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}
