package io.github.hypershell

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun HyperShellApp(
    viewModel: HyperShellViewModel,
    modifier: Modifier = Modifier,
) = io.github.hypershell.ui.HyperShellApp(viewModel = viewModel, modifier = modifier)

