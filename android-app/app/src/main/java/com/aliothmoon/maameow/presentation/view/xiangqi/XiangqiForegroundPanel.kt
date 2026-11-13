package com.aliothmoon.maameow.presentation.view.xiangqi

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.presentation.viewmodel.BackgroundTaskViewModel

@Composable
fun XiangqiForegroundPanel(
    viewModel: BackgroundTaskViewModel?,
    onClose: () -> Unit,
    onHome: () -> Unit,
    isLocked: Boolean,
    onLockToggle: (Boolean) -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize(), shape = MaterialTheme.shapes.medium) {
        Column {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                Text(stringResource(R.string.xiangqi_task_title), modifier = Modifier.weight(1f).padding(12.dp))
                IconButton(onClick = onHome) {
                    Icon(Icons.Default.Home, stringResource(R.string.panel_cd_go_home))
                }
                IconButton(onClick = { onLockToggle(!isLocked) }) {
                    Icon(if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                        stringResource(R.string.xiangqi_panel_lock))
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, stringResource(R.string.xiangqi_panel_close))
                }
            }
            HorizontalDivider()
            if (viewModel == null) {
                Text(stringResource(R.string.xiangqi_panel_open_app), Modifier.padding(16.dp))
            } else {
                XiangqiTaskView(viewModel, floatingPanel = true)
            }
        }
    }
}
