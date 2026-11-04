package com.zeeykhm.tb.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.zeeykhm.tb.R

@Composable
fun SourceDialog(
    currentUrl: String,
    onSave: (String) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    var value by rememberSaveable { mutableStateOf(currentUrl) }
    var touched by rememberSaveable { mutableStateOf(false) }

    val trimmed = value.trim()
    val validation = when {
        trimmed.isEmpty() -> stringResource(R.string.url_required)
        !trimmed.startsWith("http://", ignoreCase = true) &&
            !trimmed.startsWith("https://", ignoreCase = true) ->
            stringResource(R.string.url_invalid)
        else -> null
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = TbSurface,
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.07f)),
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = stringResource(R.string.source_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = TbText,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.source_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = TbMuted,
                )
                Spacer(Modifier.height(18.dp))

                OutlinedTextField(
                    value = value,
                    onValueChange = {
                        value = it
                        touched = true
                    },
                    singleLine = true,
                    label = { Text(stringResource(R.string.source_label)) },
                    isError = touched && validation != null,
                    supportingText = {
                        if (touched && validation != null) {
                            Text(validation)
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (validation == null) onSave(trimmed)
                        },
                    ),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = TbAccent,
                        cursorColor = TbAccent,
                        focusedTextColor = TbText,
                        unfocusedTextColor = TbText,
                        focusedLabelColor = TbAccent,
                        unfocusedLabelColor = TbMuted,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )

                TextButton(
                    onClick = onReset,
                    contentPadding = PaddingValues(0.dp),
                ) {
                    Text(
                        text = stringResource(R.string.reset_default),
                        color = TbMuted,
                    )
                }

                Spacer(Modifier.height(8.dp))

                Row(
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(
                            text = stringResource(R.string.cancel),
                            color = TbMuted,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { onSave(trimmed) },
                        enabled = validation == null,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = TbAccent,
                            contentColor = Color.White,
                        ),
                    ) {
                        Text(stringResource(R.string.save))
                    }
                }
            }
        }
    }
}
