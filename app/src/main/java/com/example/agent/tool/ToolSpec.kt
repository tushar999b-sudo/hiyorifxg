package com.example.agent.tool

import org.json.JSONArray
import org.json.JSONObject

enum class ParamType {
    STRING,
    INTEGER,
    NUMBER,
    BOOLEAN
}

data class ToolParamSpec(
    val name: String,
    val type: ParamType,
    val description: String,
    val required: Boolean = true,
    val enumValues: List<String>? = null
)

data class ToolSpec(
    val name: String,
    val description: String,
    val parameters: List<ToolParamSpec> = emptyList(),
    val permissionLevel: Int, // 0..4
    val requiresConfirmation: Boolean = false
) {
    val apiFunctionName: String
        get() = name.replace('.', '_')

    fun toOpenAiToolJson(): JSONObject {
        val props = JSONObject()
        val req = JSONArray()
        parameters.forEach { param ->
            val pObj = JSONObject().apply {
                put(
                    "type",
                    when (param.type) {
                        ParamType.STRING -> "string"
                        ParamType.INTEGER -> "integer"
                        ParamType.NUMBER -> "number"
                        ParamType.BOOLEAN -> "boolean"
                    }
                )
                put("description", param.description)
                if (!param.enumValues.isNullOrEmpty()) {
                    put("enum", JSONArray(param.enumValues))
                }
            }
            props.put(param.name, pObj)
            if (param.required) {
                req.put(param.name)
            }
        }
        val paramsSchema = JSONObject().apply {
            put("type", "object")
            put("properties", props)
            if (req.length() > 0) {
                put("required", req)
            }
        }
        return JSONObject().apply {
            put("type", "function")
            put(
                "function",
                JSONObject().apply {
                    put("name", apiFunctionName)
                    put("description", description)
                    put("parameters", paramsSchema)
                }
            )
        }
    }

    companion object {
        const val MAX_TOOLS_PER_REQUEST = 12

        val ALL_TOOLS: List<ToolSpec> = listOf(
            // Core 12 everyday tools first
            ToolSpec(
                name = "open_app",
                description = "Use when the user wants to open or launch an installed Android app. Hinglish: 'WhatsApp kholo.' English: 'Open YouTube.'",
                parameters = listOf(
                    ToolParamSpec("app_name", ParamType.STRING, "App name (e.g. WhatsApp, YouTube, Chrome, Settings) or package name", required = true)
                ),
                permissionLevel = 1
            ),
            ToolSpec(
                name = "web_search",
                description = "Use when the user wants to search Google on the web. Hinglish: 'Google pe weather search karo.' English: 'Search Google for latest news.'",
                parameters = listOf(
                    ToolParamSpec("query", ParamType.STRING, "Search query", required = true),
                    ToolParamSpec("chrome", ParamType.BOOLEAN, "True to open in Chrome", required = false)
                ),
                permissionLevel = 1
            ),
            ToolSpec(
                name = "youtube_search",
                description = "Use when the user wants to search or play videos or music on YouTube. Hinglish: 'YouTube pe lofi songs chalao.' English: 'Search YouTube for cooking videos.'",
                parameters = listOf(
                    ToolParamSpec("query", ParamType.STRING, "YouTube search query", required = true)
                ),
                permissionLevel = 1
            ),
            ToolSpec(
                name = "make_call",
                description = "Use when the user wants to call a contact or phone number. Hinglish: 'Mom ko call lagao.' English: 'Call Rahul on his phone.'",
                parameters = listOf(
                    ToolParamSpec("name_or_number", ParamType.STRING, "Contact name or phone number", required = true)
                ),
                permissionLevel = 3
            ),
            ToolSpec(
                name = "send_sms",
                description = "Use when the user wants to send a text SMS to a contact or number. Hinglish: 'Aman ko SMS bhejo ki main aa raha hoon.' English: 'Send an SMS to Mom saying I reached safely.'",
                parameters = listOf(
                    ToolParamSpec("name_or_number", ParamType.STRING, "Contact name or phone number", required = true),
                    ToolParamSpec("text", ParamType.STRING, "SMS message text", required = true)
                ),
                permissionLevel = 3
            ),
            ToolSpec(
                name = "send_whatsapp",
                description = "Use when the user wants to send a WhatsApp message to someone. Hinglish: 'WhatsApp pe Priya ko Hi bhejo.' English: 'Send a WhatsApp message to John saying hello.'",
                parameters = listOf(
                    ToolParamSpec("phone_number", ParamType.STRING, "Phone number or contact name", required = true),
                    ToolParamSpec("text", ParamType.STRING, "WhatsApp message text", required = true)
                ),
                permissionLevel = 3
            ),
            ToolSpec(
                name = "torch",
                description = "Use when the user wants to turn the phone flashlight or torch on or off. Hinglish: 'Flashlight on karo.' English: 'Turn off the torch.'",
                parameters = listOf(
                    ToolParamSpec(
                        "state",
                        ParamType.STRING,
                        "Torch state: on or off",
                        required = true,
                        enumValues = listOf("on", "off")
                    )
                ),
                permissionLevel = 1
            ),
            ToolSpec(
                name = "volume",
                description = "Use when the user wants to change music, ring, alarm, notification, or call volume. Hinglish: 'Music volume 80 kar do.' English: 'Set ring volume to 50.'",
                parameters = listOf(
                    ToolParamSpec(
                        "stream",
                        ParamType.STRING,
                        "Audio stream",
                        required = true,
                        enumValues = listOf("music", "ring", "alarm", "notification", "call")
                    ),
                    ToolParamSpec("level", ParamType.INTEGER, "Target volume level", required = true)
                ),
                permissionLevel = 1
            ),
            ToolSpec(
                name = "brightness",
                description = "Use when the user wants to adjust screen brightness from 0 to 255. Hinglish: 'Screen brightness kam kar do.' English: 'Set screen brightness to 200.'",
                parameters = listOf(
                    ToolParamSpec("level", ParamType.INTEGER, "Brightness level from 0 to 255", required = true)
                ),
                permissionLevel = 1
            ),
            ToolSpec(
                name = "clear_cache",
                description = "Use when the user asks to optimize the phone, free RAM, or clear cache. Hinglish: 'Phone optimize karo ya cache clear karo.' English: 'Optimize the device and clear cache.'",
                parameters = emptyList(),
                permissionLevel = 1
            ),
            ToolSpec(
                name = "device_info",
                description = "Use when the user asks about battery percentage, charging, RAM, storage, or Wi-Fi status. Hinglish: 'Battery kitni hai aur storage kitna bacha hai?' English: 'Check battery level and free storage.'",
                parameters = emptyList(),
                permissionLevel = 0
            ),
            ToolSpec(
                name = "screen_read",
                description = "Use when the user wants to read or inspect what is currently on the phone screen. Hinglish: 'Screen pe kya likha hai padho.' English: 'Read the current screen content.'",
                parameters = emptyList(),
                permissionLevel = 0
            ),

            // Additional specialized tools (swapped into the 12-tool window when relevant)
            ToolSpec(
                name = "open_url",
                description = "Use when the user wants to open a specific website URL. Hinglish: 'github.com kholo.' English: 'Open https://example.com in browser.'",
                parameters = listOf(
                    ToolParamSpec("url", ParamType.STRING, "Web URL to open", required = true),
                    ToolParamSpec("chrome", ParamType.BOOLEAN, "True to open in Chrome", required = false)
                ),
                permissionLevel = 1
            ),
            ToolSpec(
                name = "read_sms",
                description = "Use when the user wants to read recent SMS messages from the inbox. Hinglish: 'Mere last 5 SMS padho.' English: 'Read my recent SMS messages.'",
                parameters = listOf(
                    ToolParamSpec("count", ParamType.INTEGER, "Number of recent SMS messages", required = false)
                ),
                permissionLevel = 0
            ),
            ToolSpec(
                name = "read_notifications",
                description = "Use when the user wants to check active phone notifications. Hinglish: 'Koi naya notification aaya hai kya?' English: 'Read my active notifications.'",
                parameters = listOf(
                    ToolParamSpec("count", ParamType.INTEGER, "Max notifications to return", required = false)
                ),
                permissionLevel = 0
            ),
            ToolSpec(
                name = "reply_notification",
                description = "Use when the user wants to reply directly to an active notification. Hinglish: 'Us notification pe reply karo ki ok.' English: 'Reply to the notification from Alex.'",
                parameters = listOf(
                    ToolParamSpec("key", ParamType.STRING, "Notification key, sender name, or app package", required = true),
                    ToolParamSpec("reply_text", ParamType.STRING, "Reply message text", required = true)
                ),
                permissionLevel = 3
            ),
            ToolSpec(
                name = "tap_text",
                description = "Use when the user wants to tap a button or text visible on the screen. Hinglish: 'Submit button pe tap karo.' English: 'Tap on the Settings text on screen.'",
                parameters = listOf(
                    ToolParamSpec("text", ParamType.STRING, "Visible text or description to tap", required = true)
                ),
                permissionLevel = 2
            ),
            ToolSpec(
                name = "type_text",
                description = "Use when the user wants to type text into an input box on the screen. Hinglish: 'Search box mein hello type karo.' English: 'Type my email into the input field.'",
                parameters = listOf(
                    ToolParamSpec("text", ParamType.STRING, "Text to type", required = true),
                    ToolParamSpec("target", ParamType.STRING, "Optional hint or label of target field", required = false)
                ),
                permissionLevel = 2
            ),
            ToolSpec(
                name = "swipe",
                description = "Use when the user wants to scroll or swipe the screen up, down, left, or right. Hinglish: 'Screen neeche scroll karo.' English: 'Swipe up on the screen.'",
                parameters = listOf(
                    ToolParamSpec(
                        "direction",
                        ParamType.STRING,
                        "Swipe direction",
                        required = true,
                        enumValues = listOf("up", "down", "left", "right")
                    )
                ),
                permissionLevel = 2
            ),
            ToolSpec(
                name = "global_home",
                description = "Use when the user wants to go to the Android Home screen. Hinglish: 'Home screen pe jao.' English: 'Go to the home screen.'",
                parameters = emptyList(),
                permissionLevel = 2
            ),
            ToolSpec(
                name = "global_recents",
                description = "Use when the user wants to open recent apps overview. Hinglish: 'Recent apps dikhao.' English: 'Open recent apps.'",
                parameters = emptyList(),
                permissionLevel = 2
            ),
            ToolSpec(
                name = "do_not_disturb",
                description = "Use when the user wants to turn Do Not Disturb mode on or off. Hinglish: 'DND on kar do.' English: 'Turn off Do Not Disturb.'",
                parameters = listOf(
                    ToolParamSpec(
                        "state",
                        ParamType.STRING,
                        "DND state: on or off",
                        required = true,
                        enumValues = listOf("on", "off")
                    )
                ),
                permissionLevel = 1
            ),
            ToolSpec(
                name = "wifi_panel",
                description = "Use when the user wants to open Wi-Fi settings panel. Hinglish: 'Wi-Fi settings kholo.' English: 'Open the Wi-Fi panel.'",
                parameters = emptyList(),
                permissionLevel = 1
            ),
            ToolSpec(
                name = "bluetooth_panel",
                description = "Use when the user wants to open Bluetooth settings page. Hinglish: 'Bluetooth settings kholo.' English: 'Open Bluetooth settings.'",
                parameters = emptyList(),
                permissionLevel = 1
            ),
            ToolSpec(
                name = "memory_search",
                description = "Use when the user asks what Hiyori remembers about a topic or preference. Hinglish: 'Meri coffee preference yaad hai?' English: 'Search memory for my project notes.'",
                parameters = listOf(
                    ToolParamSpec("query", ParamType.STRING, "Search keyword or phrase", required = true)
                ),
                permissionLevel = 0
            ),
            ToolSpec(
                name = "memory_save",
                description = "Use when the user asks Hiyori to remember a fact, preference, or project detail. Hinglish: 'Yaad rakhna mujhe chai pasand hai.' English: 'Remember that my birthday is in May.'",
                parameters = listOf(
                    ToolParamSpec("content", ParamType.STRING, "Fact or preference to remember", required = true),
                    ToolParamSpec(
                        "category",
                        ParamType.STRING,
                        "Category of memory",
                        required = false,
                        enumValues = listOf("PREFERENCE", "PROJECT", "FACT", "SYSTEM")
                    ),
                    ToolParamSpec("importance", ParamType.INTEGER, "Importance 1 to 5", required = false)
                ),
                permissionLevel = 1
            ),
            ToolSpec(
                name = "memory_forget",
                description = "Use when the user asks Hiyori to forget or delete a saved memory. Hinglish: 'Meri purani memory bhool jao.' English: 'Forget the saved note about my password.'",
                parameters = listOf(
                    ToolParamSpec("query", ParamType.STRING, "Memory text or keyword to forget", required = true)
                ),
                permissionLevel = 4,
                requiresConfirmation = true
            ),
            ToolSpec(
                name = "file_create",
                description = "Use when the user wants to create a new text file in storage. Hinglish: 'notes.txt naam ki file banao.' English: 'Create a file named todo.txt with my tasks.'",
                parameters = listOf(
                    ToolParamSpec("path", ParamType.STRING, "File path or name", required = true),
                    ToolParamSpec("content", ParamType.STRING, "Text content to write", required = true)
                ),
                permissionLevel = 2
            ),
            ToolSpec(
                name = "file_read",
                description = "Use when the user wants to read the text inside a file. Hinglish: 'notes.txt file padho.' English: 'Read the contents of todo.txt.'",
                parameters = listOf(
                    ToolParamSpec("path", ParamType.STRING, "File path or name", required = true)
                ),
                permissionLevel = 0
            ),
            ToolSpec(
                name = "file_write",
                description = "Use when the user wants to write or append text to an existing file. Hinglish: 'notes.txt mein ek line add karo.' English: 'Append a note to todo.txt.'",
                parameters = listOf(
                    ToolParamSpec("path", ParamType.STRING, "File path or name", required = true),
                    ToolParamSpec("content", ParamType.STRING, "Content to write", required = true),
                    ToolParamSpec("append", ParamType.BOOLEAN, "True to append instead of overwrite", required = false)
                ),
                permissionLevel = 2
            ),
            ToolSpec(
                name = "file_list",
                description = "Use when the user wants to list files in a folder. Hinglish: 'Documents folder mein kya files hain?' English: 'List files in the Hiyori folder.'",
                parameters = listOf(
                    ToolParamSpec("path", ParamType.STRING, "Directory path", required = false)
                ),
                permissionLevel = 0
            ),
            ToolSpec(
                name = "file_move",
                description = "Use when the user wants to move a file to another folder. Hinglish: 'notes.txt ko Documents mein move karo.' English: 'Move report.txt to Backup folder.'",
                parameters = listOf(
                    ToolParamSpec("source_path", ParamType.STRING, "Current file path", required = true),
                    ToolParamSpec("dest_path", ParamType.STRING, "Destination file path", required = true)
                ),
                permissionLevel = 2
            ),
            ToolSpec(
                name = "file_rename",
                description = "Use when the user wants to rename a file. Hinglish: 'notes.txt ka naam list.txt kar do.' English: 'Rename draft.txt to final.txt.'",
                parameters = listOf(
                    ToolParamSpec("source_path", ParamType.STRING, "Current file path", required = true),
                    ToolParamSpec("new_name", ParamType.STRING, "New file name or path", required = true)
                ),
                permissionLevel = 2
            ),
            ToolSpec(
                name = "file_search",
                description = "Use when the user wants to find a file by name in storage. Hinglish: 'Resume naam ki file dhoondho.' English: 'Search for files matching invoice.'",
                parameters = listOf(
                    ToolParamSpec("query", ParamType.STRING, "File name or keyword", required = true)
                ),
                permissionLevel = 0
            ),
            ToolSpec(
                name = "file_delete",
                description = "Use when the user wants to delete a file from storage. Hinglish: 'old.txt file delete kar do.' English: 'Delete temp.txt from storage.'",
                parameters = listOf(
                    ToolParamSpec("path", ParamType.STRING, "Exact file path to delete", required = true)
                ),
                permissionLevel = 4,
                requiresConfirmation = true
            ),
            ToolSpec(
                name = "calendar_events",
                description = "Use when the user wants to check upcoming calendar events or add a new event. Hinglish: 'Aaj ke calendar events batao.' English: 'Create a calendar event for Meeting tomorrow.'",
                parameters = listOf(
                    ToolParamSpec("days_ahead", ParamType.INTEGER, "Number of days ahead to query", required = false),
                    ToolParamSpec("title", ParamType.STRING, "Title for a new calendar event", required = false),
                    ToolParamSpec("start_time_millis", ParamType.INTEGER, "Event start time in epoch millis", required = false),
                    ToolParamSpec("end_time_millis", ParamType.INTEGER, "Event end time in epoch millis", required = false)
                ),
                permissionLevel = 1
            ),
            ToolSpec(
                name = "location",
                description = "Use when the user asks for their current GPS location or coordinates. Hinglish: 'Meri current location kya hai?' English: 'Get my current GPS location.'",
                parameters = emptyList(),
                permissionLevel = 0
            ),
            ToolSpec(
                name = "shizuku_toggle_wifi",
                description = "Use when the user wants to turn Wi-Fi on or off directly via Shizuku. Hinglish: 'Wi-Fi band kar do.' English: 'Turn Wi-Fi on via Shizuku.'",
                parameters = listOf(
                    ToolParamSpec("on", ParamType.BOOLEAN, "True to enable, false to disable", required = true)
                ),
                permissionLevel = 2
            ),
            ToolSpec(
                name = "shizuku_toggle_bluetooth",
                description = "Use when the user wants to turn Bluetooth on or off directly via Shizuku. Hinglish: 'Bluetooth chalu karo.' English: 'Turn Bluetooth off via Shizuku.'",
                parameters = listOf(
                    ToolParamSpec("on", ParamType.BOOLEAN, "True to enable, false to disable", required = true)
                ),
                permissionLevel = 2
            ),
            ToolSpec(
                name = "shizuku_screenshot",
                description = "Use when the user wants to take a screenshot via Shizuku. Hinglish: 'Screenshot le lo.' English: 'Take a screenshot now.'",
                parameters = listOf(
                    ToolParamSpec("path", ParamType.STRING, "Path inside /sdcard/Pictures/Hiyori/ ending with .png", required = true)
                ),
                permissionLevel = 2
            )
        )

        private val ALIASES: Map<String, String> = mapOf(
            "memory.search" to "memory_search",
            "memory.save" to "memory_save",
            "memory.forget" to "memory_forget",
            "mobile.open_app" to "open_app",
            "web.open_url" to "open_url",
            "web.search" to "web_search",
            "web.youtube_search" to "youtube_search",
            "mobile.make_call" to "make_call",
            "mobile.send_message" to "send_sms",
            "mobile.read_sms" to "read_sms",
            "mobile.read_notifications" to "read_notifications",
            "mobile.reply_notification" to "reply_notification",
            "mobile.screen_inspect" to "screen_read",
            "mobile.tap_element" to "tap_text",
            "mobile.type_text" to "type_text",
            "mobile.swipe" to "swipe",
            "mobile.scroll" to "swipe",
            "mobile.device_info" to "device_info",
            "mobile.optimize_device" to "clear_cache",
            "optimize" to "clear_cache",
            "file.create_document" to "file_create",
            "file.read" to "file_read",
            "file.write" to "file_write",
            "file.list" to "file_list",
            "file.move" to "file_move",
            "file.rename" to "file_rename",
            "file.search" to "file_search",
            "file.delete" to "file_delete"
        )

        fun findByName(rawName: String): ToolSpec? {
            val cleaned = rawName.trim()
            val mapped = ALIASES[cleaned] ?: cleaned
            return ALL_TOOLS.find {
                it.name.equals(mapped, ignoreCase = true) ||
                    it.apiFunctionName.equals(mapped, ignoreCase = true)
            }
        }

        /**
         * Selects at most [maxTools] (default 12) tools per request so small local models
         * and Gemini stay fast and accurate while still having access to specialized tools
         * when the user's message mentions them.
         */
        fun selectToolsForRequest(userMessage: String, maxTools: Int = MAX_TOOLS_PER_REQUEST): List<ToolSpec> {
            val lower = userMessage.lowercase()
            val prioritizedNames = linkedSetOf<String>()

            if (lower.contains("file") || lower.contains("folder") || lower.contains("note") ||
                lower.contains("txt") || lower.contains("document") || lower.contains("delete") ||
                lower.contains("rename") || lower.contains("move")
            ) {
                prioritizedNames.addAll(
                    listOf("file_create", "file_read", "file_write", "file_list", "file_search", "file_rename", "file_move", "file_delete")
                )
            }
            if (lower.contains("remember") || lower.contains("memory") || lower.contains("yaad") ||
                lower.contains("forget") || lower.contains("bhool")
            ) {
                prioritizedNames.addAll(listOf("memory_save", "memory_search", "memory_forget"))
            }
            if (lower.contains("wifi") || lower.contains("wi-fi")) {
                prioritizedNames.addAll(listOf("shizuku_toggle_wifi", "wifi_panel"))
            }
            if (lower.contains("bluetooth")) {
                prioritizedNames.addAll(listOf("shizuku_toggle_bluetooth", "bluetooth_panel"))
            }
            if (lower.contains("dnd") || lower.contains("do not disturb") || lower.contains("silent")) {
                prioritizedNames.add("do_not_disturb")
            }
            if (lower.contains("screenshot")) {
                prioritizedNames.add("shizuku_screenshot")
            }
            if (lower.contains("calendar") || lower.contains("event") || lower.contains("meeting") || lower.contains("schedule")) {
                prioritizedNames.add("calendar_events")
            }
            if (lower.contains("location") || lower.contains("gps") || lower.contains("where am i") || lower.contains("kahan")) {
                prioritizedNames.add("location")
            }
            if (lower.contains("sms") || lower.contains("inbox")) {
                prioritizedNames.addAll(listOf("read_sms", "send_sms"))
            }
            if (lower.contains("notification") || lower.contains("reply")) {
                prioritizedNames.addAll(listOf("read_notifications", "reply_notification"))
            }
            if (lower.contains("tap") || lower.contains("click") || lower.contains("type") ||
                lower.contains("swipe") || lower.contains("scroll") || lower.contains("home") || lower.contains("recent")
            ) {
                prioritizedNames.addAll(listOf("screen_read", "tap_text", "type_text", "swipe", "global_home", "global_recents"))
            }
            if (lower.contains("http") || lower.contains(".com") || lower.contains("website") || lower.contains("url") || lower.contains("link")) {
                prioritizedNames.add("open_url")
            }

            val selected = mutableListOf<ToolSpec>()
            for (name in prioritizedNames) {
                val spec = findByName(name)
                if (spec != null && spec !in selected) {
                    selected.add(spec)
                }
            }
            for (spec in ALL_TOOLS) {
                if (selected.size >= maxTools) break
                if (spec !in selected) {
                    selected.add(spec)
                }
            }
            return selected.take(maxTools)
        }

        fun toOpenAiToolsJson(tools: List<ToolSpec> = ALL_TOOLS.take(MAX_TOOLS_PER_REQUEST)): JSONArray {
            val arr = JSONArray()
            tools.forEach { arr.put(it.toOpenAiToolJson()) }
            return arr
        }
    }
}
