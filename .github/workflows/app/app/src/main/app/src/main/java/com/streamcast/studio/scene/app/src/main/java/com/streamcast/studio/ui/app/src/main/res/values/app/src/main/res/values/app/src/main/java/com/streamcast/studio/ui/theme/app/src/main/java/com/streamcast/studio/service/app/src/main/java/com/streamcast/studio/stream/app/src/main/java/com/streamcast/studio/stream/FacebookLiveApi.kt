package com.streamcast.studio.stream

import android.os.Bundle
import com.facebook.AccessToken
import com.facebook.GraphRequest
import com.facebook.GraphResponse
import com.facebook.HttpMethod

object FacebookLiveApi {

    data class Page(val id: String, val name: String, val token: String)
    data class LiveInfo(val id: String, val streamUrl: String)

    fun loadPages(onResult: (List<Page>?, String?) -> Unit) {
        val token = try {
            AccessToken.getCurrentAccessToken()
        } catch (e: Exception) {
            null
        }
        if (token == null) {
            onResult(null, "আগে ফেসবুক লগইন করুন")
            return
        }
        val params = Bundle().apply { putString("fields", "id,name,access_token") }
        GraphRequest(
            token,
            "me/accounts",
            params,
            HttpMethod.GET,
            object : GraphRequest.Callback {
                override fun onCompleted(response: GraphResponse) {
                    val error = response.error
                    if (error != null) {
                        onResult(null, error.errorMessage ?: "পেজ লোড করা যায়নি")
                        return
                    }
                    val data = response.jsonObject?.optJSONArray("data")
                    val pages = mutableListOf<Page>()
                    if (data != null) {
                        for (i in 0 until data.length()) {
                            val item = data.optJSONObject(i) ?: continue
                            pages.add(
                                Page(
                                    id = item.optString("id"),
                                    name = item.optString("name"),
                                    token = item.optString("access_token")
                                )
                            )
                        }
                    }
                    onResult(pages, null)
                }
            }
        ).executeAsync()
    }

    fun createLive(page: Page, title: String, onResult: (LiveInfo?, String?) -> Unit) {
        val params = Bundle().apply {
            putString("status", "LIVE_NOW")
            putString("title", title)
            putString("access_token", page.token)
        }
        GraphRequest(
            null,
            "${page.id}/live_videos",
            params,
            HttpMethod.POST,
            object : GraphRequest.Callback {
                override fun onCompleted(response: GraphResponse) {
                    val error = response.error
                    if (error != null) {
                        onResult(null, error.errorMessage ?: "লাইভ তৈরি করা যায়নি")
                        return
                    }
                    val json = response.jsonObject
                    val id = json?.optString("id").orEmpty()
                    var url = json?.optString("secure_stream_url").orEmpty()
                    if (url.isBlank()) url = json?.optString("stream_url").orEmpty()
                    if (id.isBlank() || url.isBlank()) {
                        onResult(null, "ফেসবুক stream URL দেয়নি")
                    } else {
                        onResult(LiveInfo(id, url), null)
                    }
                }
            }
        ).executeAsync()
    }

    fun endLive(page: Page, liveId: String) {
        val params = Bundle().apply {
            putString("end_live_video", "true")
            putString("access_token", page.token)
        }
        GraphRequest(null, liveId, params, HttpMethod.POST, null).executeAsync()
    }
}
