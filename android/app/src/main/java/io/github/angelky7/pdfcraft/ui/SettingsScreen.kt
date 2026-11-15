package io.github.angelky7.pdfcraft.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.angelky7.pdfcraft.R
import io.github.angelky7.pdfcraft.data.Prefs

/**
 * The few choices worth offering, and the facts worth publishing.
 *
 * Nothing here needs an account, so there is no sign-in row and no plan to sell: the top of the
 * screen says what the app does with a document (nothing, off this device), and the rest is the
 * three settings that change how it feels plus the small print that says who wrote it.
 */
@Composable
fun SettingsScreen(
    theme: String,
    fingerWrites: Boolean,
    singlePage: Boolean,
    version: String,
    docCount: Int,
    totalBytes: Long,
    onTheme: (String) -> Unit,
    onFingerWrites: (Boolean) -> Unit,
    onSinglePage: (Boolean) -> Unit,
    onClearAll: () -> Unit,
) {
    var sheet by remember { mutableStateOf(0) }
    var confirmClear by remember { mutableStateOf(false) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val width = if (maxWidth > 900.dp) 900.dp else maxWidth

        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(width)
                    .align(Alignment.TopCenter),
                contentPadding = PaddingValues(bottom = 32.dp),
            ) {
                item {
                    Column(Modifier.padding(horizontal = 20.dp)) {
                        ScreenTitle(stringResource(R.string.settings_title))
                    }
                }

                item {
                    Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                        GroupCard {
                            Column(Modifier.padding(16.dp)) {
                                Text(
                                    text = stringResource(R.string.settings_banner_title),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = stringResource(R.string.settings_banner_body),
                                    fontSize = 11.sp,
                                    lineHeight = 16.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    text = stringResource(
                                        R.string.settings_storage_value,
                                        docCount,
                                        humanSize(totalBytes),
                                    ),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }

                item {
                    SectionHeader(
                        text = stringResource(R.string.settings_group_prefs),
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                }

                item {
                    GroupCard(Modifier.padding(horizontal = 20.dp)) {
                        SettingsRow(
                            title = stringResource(R.string.settings_finger),
                            body = stringResource(R.string.settings_finger_body),
                            trailing = {
                                Switch(checked = fingerWrites, onCheckedChange = onFingerWrites)
                            },
                        )
                        Hairline()
                        SettingsRow(
                            title = stringResource(R.string.settings_single),
                            body = stringResource(R.string.settings_single_body),
                            trailing = {
                                Switch(checked = singlePage, onCheckedChange = onSinglePage)
                            },
                        )
                        Hairline()
                        SettingsRow(
                            title = stringResource(R.string.settings_theme),
                            value = stringResource(
                                when (theme) {
                                    Prefs.ThemeLight -> R.string.settings_theme_light
                                    Prefs.ThemeDark -> R.string.settings_theme_dark
                                    else -> R.string.settings_theme_system
                                },
                            ),
                            onClick = {
                                onTheme(
                                    when (theme) {
                                        Prefs.ThemeSystem -> Prefs.ThemeDark
                                        Prefs.ThemeDark -> Prefs.ThemeLight
                                        else -> Prefs.ThemeSystem
                                    },
                                )
                            },
                        )
                    }
                }

                item {
                    SectionHeader(
                        text = stringResource(R.string.settings_group_about),
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                }

                item {
                    GroupCard(Modifier.padding(horizontal = 20.dp)) {
                        SettingsRow(
                            title = stringResource(R.string.settings_version),
                            value = version,
                        )
                        Hairline()
                        SettingsRow(
                            title = stringResource(R.string.settings_license),
                            onClick = { sheet = 1 },
                        )
                        Hairline()
                        SettingsRow(
                            title = stringResource(R.string.settings_upstream),
                            value = "storytold/pdfcraft",
                            onClick = { sheet = 2 },
                        )
                        Hairline()
                        SettingsRow(
                            title = stringResource(R.string.settings_privacy),
                            onClick = { sheet = 3 },
                        )
                    }
                }

                if (docCount > 0) {
                    item {
                        Spacer(Modifier.height(4.dp))
                        Hairline()
                        SettingsRow(
                            title = stringResource(R.string.settings_clear),
                            body = stringResource(R.string.settings_clear_body),
                            destructive = true,
                            onClick = { confirmClear = true },
                        )
                    }
                }

                item {
                    Footnote(
                        text = stringResource(R.string.settings_footnote),
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp),
                    )
                }
            }
        }
    }

    if (sheet != 0) {
        val (title, body) = when (sheet) {
            1 -> R.string.settings_license to R.string.settings_license_body
            2 -> R.string.settings_upstream to R.string.settings_upstream_body
            else -> R.string.settings_privacy to R.string.settings_privacy_body
        }
        AlertDialog(
            onDismissRequest = { sheet = 0 },
            title = { Text(stringResource(title)) },
            text = { Text(stringResource(body), fontSize = 13.sp, lineHeight = 20.sp) },
            confirmButton = {
                TextButton(onClick = { sheet = 0 }) { Text(stringResource(R.string.action_dismiss)) }
            },
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.files_clear_confirm_title)) },
            text = { Text(stringResource(R.string.files_clear_confirm_body, docCount, humanSize(totalBytes))) },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    onClearAll()
                }) {
                    Text(stringResource(R.string.action_remove_all), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}
