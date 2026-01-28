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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ChatAccessibilityService : AccessibilityService() {

    private val targetPackages = setOf(
        "com.whatsapp",
        "org.thoughtcrime.securesms", // Signal
        "org.telegram.messenger",
        "com.telegram.messenger"
    )

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Stop if toggle is OFF or event is invalid
        if (!ChatSpyManager.isSpyingEnabled.value || event == null) return

        // Check if the event comes from a supported chat app
        if (event.packageName?.toString() !in targetPackages) return

        // specific trigger: User CLICKED something
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            // event.source creates a new node instance that MUST be recycled
            val sourceNode = event.source ?: return

            try {
                val allTextParts = mutableListOf<String>()
                collectAllText(sourceNode, allTextParts)

                // Heuristic: Longest text is likely the message body
                val bestText = allTextParts.maxByOrNull { it.length }

                if (!bestText.isNullOrBlank()) {
                    CoroutineScope(Dispatchers.Main).launch {
                        ChatSpyManager.onMessageClicked(bestText, event.packageName.toString())
                    }
                }
            } finally {
                // CRITICAL: Recycle the root node to prevent memory leaks
                sourceNode.recycle()
            }
        }
    }

    private fun collectAllText(node: AccessibilityNodeInfo?, targetList: MutableList<String>) {
        if (node == null) return

        // 1. Check content description (often used by accessibility for the whole message)
        if (!node.contentDescription.isNullOrBlank()) {
            targetList.add(node.contentDescription.toString())
        }

        // 2. Check actual text
        if (!node.text.isNullOrBlank()) {
            targetList.add(node.text.toString())
        }

        // 3. Recursively check all children to find hidden text views
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                collectAllText(child, targetList)
                // CRITICAL: Recycle the child node immediately after processing
                child.recycle()
            }
        }
    }

    override fun onInterrupt() {}
}
