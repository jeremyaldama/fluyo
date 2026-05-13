package com.qolve.fluyo.presentation.screens.scan

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.qolve.fluyo.R
import com.qolve.fluyo.domain.model.Category
import com.qolve.fluyo.domain.model.DetectedField
import com.qolve.fluyo.presentation.util.iconForToken
import com.qolve.fluyo.presentation.util.parseHexColor
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OcrConfirmScreen(
    onClose: () -> Unit,
    onSaved: () -> Unit,
    viewModel: OcrConfirmViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.savedOk) {
        if (state.savedOk) onSaved()
    }
    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeError()
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.ocr_title)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (state.isProcessing) {
            ProcessingState(modifier = Modifier.padding(padding))
        } else {
            ConfirmForm(
                state = state,
                onAmountChange = viewModel::onAmountChange,
                onRecipientChange = viewModel::onRecipientChange,
                onDescriptionChange = viewModel::onDescriptionChange,
                onCategorySelect = viewModel::onCategorySelect,
                onSave = viewModel::save,
                contentPadding = padding,
            )
        }
    }
}

@Composable
private fun ProcessingState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(strokeWidth = 3.dp)
            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.ocr_processing),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.ocr_processing_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ConfirmForm(
    state: OcrConfirmUiState,
    onAmountChange: (String) -> Unit,
    onRecipientChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onCategorySelect: (String) -> Unit,
    onSave: () -> Unit,
    contentPadding: PaddingValues,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ReceiptPreview(uri = state.imageUri)

        if (state.parseError) {
            ParseErrorBanner()
        } else if (state.autoDetected.isNotEmpty()) {
            DetectedBanner(state.autoDetected.size)
        }

        Spacer(Modifier.height(4.dp))

        AmountField(
            value = state.amountInput,
            autoDetected = DetectedField.AMOUNT in state.autoDetected,
            onChange = onAmountChange,
        )

        FieldLabel(stringResource(R.string.ocr_category_label))
        CategoryPicker(
            categories = state.categories,
            selectedId = state.selectedCategoryId,
            onSelect = onCategorySelect,
        )

        FieldLabel(stringResource(R.string.ocr_recipient_label))
        DetectableTextField(
            value = state.recipient,
            onChange = onRecipientChange,
            placeholder = stringResource(R.string.ocr_recipient_hint),
            autoDetected = DetectedField.RECIPIENT in state.autoDetected,
        )

        FieldLabel(stringResource(R.string.ocr_date_label))
        DateChip(
            date = state.date,
            autoDetected = DetectedField.DATE in state.autoDetected,
        )

        FieldLabel(stringResource(R.string.manual_entry_description_label))
        OutlinedTextField(
            value = state.description,
            onValueChange = onDescriptionChange,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = onSave,
            enabled = state.canSave,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            if (state.isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Text(stringResource(R.string.ocr_save))
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun ReceiptPreview(uri: Uri?) {
    if (uri == null) return
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        AsyncImage(
            model = uri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun DetectedBanner(count: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.ocr_detected_banner, count),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun ParseErrorBanner() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.ocr_parse_error),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

@Composable
private fun AmountField(
    value: String,
    autoDetected: Boolean,
    onChange: (String) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.ocr_amount_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (autoDetected) {
                    Spacer(Modifier.width(8.dp))
                    DetectedChip()
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.onboarding_currency_prefix),
                    style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Light),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(
                    value = value,
                    onValueChange = onChange,
                    placeholder = {
                        Text(
                            text = "0.00",
                            style = TextStyle(fontSize = 36.sp, fontWeight = FontWeight.Light),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 36.sp, fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun DetectableTextField(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    autoDetected: Boolean,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (autoDetected) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DetectedChip()
                Spacer(Modifier.height(4.dp))
            }
            Spacer(Modifier.height(6.dp))
        }
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            placeholder = { Text(placeholder) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun DateChip(
    date: java.time.LocalDate,
    autoDetected: Boolean,
) {
    val fmt = remember {
        DateTimeFormatter.ofPattern("d 'de' MMMM, yyyy", Locale.forLanguageTag("es-PE"))
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Text(
                text = date.format(fmt),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        if (autoDetected) {
            Spacer(Modifier.width(8.dp))
            DetectedChip()
        }
    }
}

@Composable
private fun DetectedChip() {
    AssistChip(
        onClick = {},
        label = { Text(stringResource(R.string.ocr_detected_chip)) },
        leadingIcon = {
            Icon(
                imageVector = Icons.Outlined.AutoAwesome,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
            )
        },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            labelColor = MaterialTheme.colorScheme.primary,
            leadingIconContentColor = MaterialTheme.colorScheme.primary,
        ),
    )
}

@Composable
private fun CategoryPicker(
    categories: List<Category>,
    selectedId: String?,
    onSelect: (String) -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 4.dp),
    ) {
        items(categories, key = { it.id }) { category ->
            CategoryChip(
                category = category,
                selected = category.id == selectedId,
                onClick = { onSelect(category.id) },
            )
        }
    }
}

@Composable
private fun CategoryChip(
    category: Category,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val color = parseHexColor(category.color)
    val icon = iconForToken(category.icon)
    val bg = if (selected) color.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceVariant
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(bg)
                .clickable { onClick() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = category.name,
                tint = if (selected) color else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = category.name,
            style = MaterialTheme.typography.bodySmall,
            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}
