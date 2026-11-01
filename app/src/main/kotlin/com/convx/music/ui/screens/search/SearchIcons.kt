package com.convx.music.ui.screens.search

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.Umbrella
import androidx.compose.ui.graphics.vector.ImageVector

/** One editable icon map for every Search activity, mood and small control. */
internal object SearchIcons {
    val workout = Icons.Rounded.FitnessCenter
    val focus = Icons.Rounded.CenterFocusStrong
    val sleep = Icons.Rounded.Bedtime
    val commute = Icons.Rounded.DirectionsCar
    val party = Icons.Rounded.Celebration
    val chill = Icons.Rounded.LocalCafe
    val energise = Icons.Rounded.Bolt
    val feelGood = Icons.Rounded.WbSunny
    val romance = Icons.Rounded.Favorite
    val sad = Icons.Rounded.Umbrella
    val christmas = Icons.Rounded.AcUnit
    val metal = Icons.Rounded.LocalFireDepartment
    val hipHop = Icons.Rounded.Mic

    val recent = Icons.Rounded.Schedule
    val clear = Icons.Rounded.Cancel
    val chevron = Icons.Rounded.ChevronRight
    val play = Icons.Rounded.PlayArrow
    val search = Icons.Rounded.Search

    fun activity(name: String): ImageVector? = when (name.lowercase()) {
        "workout" -> workout
        "focus" -> focus
        "sleep" -> sleep
        "commute" -> commute
        "party" -> party
        "chill" -> chill
        "energise", "energize" -> energise
        "feel-good", "feel good" -> feelGood
        "romance" -> romance
        "sad" -> sad
        "christmas" -> christmas
        "metal" -> metal
        "hip-hop", "hip hop" -> hipHop
        else -> null
    }
}
