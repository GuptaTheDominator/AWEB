package com.aweb.browser.ui.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aweb.browser.browser.FindInPageHandler

/**
 * Compact find-in-page bar — slides up from the bottom of the browser pane.
 *
 * Shows: [prev ◀] [▶ next] [query field] [N/M] [✕ close]
 */
@Composable
fun FindInPageBar(
    result      : FindInPageHandler.FindResult,
    onFind      : (String, Boolean) -> Unit,
    onClose     : () -> Unit,
    modifier    : Modifier = Modifier,
) {
    var query by remember { mutableStateOf(TextFieldValue("")) }
    val focusReq = remember { FocusRequester() }

    LaunchedEffect(Unit) { focusReq.requestFocus() }

    Surface(
        color  = Color(0xFF1A1A1A),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            // Prev
            IconButton(onClick = { onFind(query.text, false) }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.ArrowUpward, "Previous", tint = Color.White, modifier = Modifier.size(18.dp))
            }
            // Next
            IconButton(onClick = { onFind(query.text, true) }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.ArrowDownward, "Next", tint = Color.White, modifier = Modifier.size(18.dp))
            }

            // Query field
            TextField(
                value           = query,
                onValueChange   = { query = it; onFind(it.text, true) },
                singleLine      = true,
                placeholder     = { Text("Find in page…", color = Color(0xFF555555), fontSize = 13.sp) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onFind(query.text, true) }),
                shape  = RoundedCornerShape(20.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor   = Color(0xFF252525),
                    unfocusedContainerColor = Color(0xFF252525),
                    focusedTextColor        = Color.White,
                    unfocusedTextColor      = Color.White,
                    focusedIndicatorColor   = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor             = Color(0xFF2F8CFF),
                ),
                textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
                modifier  = Modifier
                    .weight(1f)
                    .focusRequester(focusReq),
            )

            // Result count
            if (result.total > 0 || query.text.isNotBlank()) {
                Text(
                    if (result.found) "${result.current}/${result.total}" else "0/0",
                    color    = if (result.found) Color(0xFF2F8CFF) else Color(0xFFFF5C7A),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }

            // Close
            IconButton(onClick = onClose, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.Close, "Close find", tint = Color(0xFF888888), modifier = Modifier.size(18.dp))
            }
        }
    }
}
