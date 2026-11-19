package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.config.PricingConfig
import com.example.domain.model.SubscriptionPlan
import com.example.domain.model.SubscriptionStatus
import com.example.i18n.UzStrings
import com.example.ui.viewmodel.PricingViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PricingScreen(
    onBack: () -> Unit,
    viewModel: PricingViewModel = viewModel()
) {
    val sub by viewModel.subscription.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(statusMessage) {
        statusMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearMessage()
        }
    }

    val isPaidActive = sub.isPaidActive()
    val dateFormat = remember { SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(UzStrings.PRICING_TITLE, style = MaterialTheme.typography.headlineMedium) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("pricing_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = UzStrings.CLOSE_BTN)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = UzStrings.PRICING_SUBTITLE,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Current Plan Banner
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(
                        containerColor = if (isPaidActive) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else if (sub.status == SubscriptionStatus.EXPIRED) {
                            MaterialTheme.colorScheme.errorContainer
                        } else {
                            MaterialTheme.colorScheme.surface
                        }
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isPaidActive) Icons.Default.Star else Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = if (sub.status == SubscriptionStatus.EXPIRED) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.primary
                            }
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${UzStrings.ACTIVE_PLAN_BADGE}: ${sub.plan.displayName}",
                                style = MaterialTheme.typography.titleMedium
                            )
                            if (isPaidActive && sub.expiresAt > 0L) {
                                Text(
                                    text = String.format(UzStrings.EXPIRES_AT_FMT, dateFormat.format(Date(sub.expiresAt))),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            } else if (sub.status == SubscriptionStatus.EXPIRED) {
                                Text(
                                    text = UzStrings.EXPIRED_PLAN_BADGE,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }

                // Comparison Table
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = UzStrings.COMPARISON_HEADER,
                            style = MaterialTheme.typography.titleLarge
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        ComparisonHeaderRow()
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        ComparisonRow(
                            feature = UzStrings.COMPARE_MUSEUMS,
                            freeVal = UzStrings.COMPARE_FREE_MUSEUMS,
                            paidVal = UzStrings.COMPARE_PAID_MUSEUMS
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        ComparisonRow(
                            feature = UzStrings.COMPARE_PHOTOS,
                            freeVal = UzStrings.COMPARE_FREE_PHOTOS,
                            paidVal = UzStrings.COMPARE_PAID_PHOTOS
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        ComparisonRow(
                            feature = UzStrings.COMPARE_STYLES,
                            freeVal = UzStrings.COMPARE_FREE_STYLES,
                            paidVal = UzStrings.COMPARE_PAID_STYLES
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        ComparisonRow(
                            feature = UzStrings.COMPARE_MUSIC,
                            freeVal = UzStrings.COMPARE_NO,
                            paidVal = UzStrings.COMPARE_YES
                        )
                    }
                }

                // Plan Cards
                PlanOptionCard(
                    title = UzStrings.PLAN_FREE_NAME,
                    price = UzStrings.PRICE_FREE,
                    subtitle = "${UzStrings.COMPARE_FREE_MUSEUMS} muzey • ${UzStrings.COMPARE_FREE_PHOTOS} • ${UzStrings.COMPARE_FREE_STYLES}",
                    isCurrent = !isPaidActive && sub.plan == SubscriptionPlan.FREE,
                    highlighted = false,
                    tag = "plan_free_btn",
                    onSelect = { viewModel.selectTestPlan(SubscriptionPlan.FREE) }
                )

                PlanOptionCard(
                    title = UzStrings.PLAN_6M_NAME,
                    price = PricingConfig.formatUzs(PricingConfig.SIX_MONTHS_PRICE_UZS),
                    subtitle = "${UzStrings.COMPARE_PAID_PHOTOS} • ${UzStrings.COMPARE_PAID_STYLES} • ${UzStrings.COMPARE_MUSIC}",
                    isCurrent = isPaidActive && sub.plan == SubscriptionPlan.SIX_MONTHS,
                    highlighted = true,
                    tag = "plan_6m_btn",
                    onSelect = { viewModel.selectTestPlan(SubscriptionPlan.SIX_MONTHS) }
                )

                PlanOptionCard(
                    title = UzStrings.PLAN_1Y_NAME,
                    price = PricingConfig.formatUzs(PricingConfig.ONE_YEAR_PRICE_UZS),
                    subtitle = "${UzStrings.COMPARE_PAID_MUSEUMS} muzey • ${UzStrings.COMPARE_PAID_PHOTOS} • Romantik & To'y • Musiqa",
                    isCurrent = isPaidActive && sub.plan == SubscriptionPlan.ONE_YEAR,
                    highlighted = true,
                    tag = "plan_1y_btn",
                    onSelect = { viewModel.selectTestPlan(SubscriptionPlan.ONE_YEAR) }
                )

                // Test subscription controls
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(UzStrings.TEST_MODE_TITLE, style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = UzStrings.TEST_MODE_DESC,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(
                            onClick = { viewModel.simulateExpiry() },
                            modifier = Modifier.testTag("test_expire_button")
                        ) {
                            Text(UzStrings.TEST_EXPIRE_BTN, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun ComparisonHeaderRow() {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "",
            modifier = Modifier.weight(1.2f),
            style = MaterialTheme.typography.labelLarge
        )
        Text(
            text = UzStrings.PLAN_FREE_NAME,
            modifier = Modifier.weight(0.9f),
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "Pullik",
            modifier = Modifier.weight(1.2f),
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun ComparisonRow(
    feature: String,
    freeVal: String,
    paidVal: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = feature,
            modifier = Modifier.weight(1.2f),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = freeVal,
            modifier = Modifier.weight(0.9f),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = paidVal,
            modifier = Modifier.weight(1.2f),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun PlanOptionCard(
    title: String,
    price: String,
    subtitle: String,
    isCurrent: Boolean,
    highlighted: Boolean,
    tag: String,
    onSelect: () -> Unit
) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            width = if (isCurrent || highlighted) 1.5.dp else 1.dp,
            color = if (isCurrent || highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = title, style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = price,
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (isCurrent) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = UzStrings.ACTIVE_PLAN_BADGE,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            if (highlighted) {
                Button(
                    onClick = onSelect,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag(tag),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text(UzStrings.SELECT_PLAN_BTN)
                }
            } else {
                OutlinedButton(
                    onClick = onSelect,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag(tag),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text(UzStrings.SELECT_PLAN_BTN)
                }
            }
        }
    }
}
