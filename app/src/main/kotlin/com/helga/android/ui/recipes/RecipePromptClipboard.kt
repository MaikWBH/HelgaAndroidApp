package com.helga.android.ui.recipes

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import com.helga.android.R

/** Die KI-Vorlage für den Markdown-Import aus `res/raw/recipe_md_prompt.txt`. */
internal fun loadRecipePrompt(context: Context): String =
    context.resources.openRawResource(R.raw.recipe_md_prompt).bufferedReader(Charsets.UTF_8).use { it.readText() }

/** Legt die KI-Vorlage in die Zwischenablage. */
internal fun copyRecipePrompt(context: Context) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(
        ClipData.newPlainText(context.getString(R.string.md_import_clip_label), loadRecipePrompt(context)),
    )
}

/** Text des ersten Zwischenablage-Eintrags, sonst leer. */
internal fun readClipboardText(context: Context): String {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return ""
    val clip = clipboard.primaryClip ?: return ""
    if (clip.itemCount == 0) return ""
    return clip.getItemAt(0).coerceToText(context)?.toString().orEmpty()
}
