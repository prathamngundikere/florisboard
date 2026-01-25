/*
 * Copyright (C) 2026 The FlorisBoard Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.patrickgold.florisboard.chatspy

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class ChatAccessibilityService : AccessibilityService() {

    private val targetPackages = setOf(
        "com.whatsapp",
        "org.thoughtcrime.securesms", // Signal
        "org.telegram.messenger"
    )

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Stop if toggle is OFF or event is invalid
        if (!ChatSpyManager.isSpyingEnabled.value || event == null) return

        // Check if the event comes from a supported chat app
        if (event.packageName?.toString() !in targetPackages) return

        // specific trigger: User CLICKED something
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            val sourceNode = event.source ?: return

            // Extract text from the clicked node or its children
            val capturedText = extractText(sourceNode)

            if (!capturedText.isNullOrBlank()) {
                ChatSpyManager.onMessageClicked(this, capturedText, event.packageName.toString())
            }
        }
    }

    private fun extractText(node: AccessibilityNodeInfo): String? {
        // 1. If this node has text, return it
        if (!node.text.isNullOrBlank()) {
            return node.text.toString()
        }

        // 2. If no text, check children (sometimes clicks land on the message container)
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val text = extractText(child)
            if (text != null) return text
        }
        return null
    }

    override fun onInterrupt() {}
}
