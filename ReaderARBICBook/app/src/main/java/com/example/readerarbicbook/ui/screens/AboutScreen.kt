package com.example.readerarbicbook.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.readerarbicbook.ui.components.*
import com.example.readerarbicbook.data.DevicePreferences
import androidx.compose.ui.platform.LocalContext

@Composable
fun AboutScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val language by DevicePreferences.getLanguage(context).collectAsState(initial = "Arabic")
    val isArabic = language == "Arabic"
    Box(
        modifier = Modifier
            .fillMaxSize()
            .luxuryGradientBackground()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = if (isArabic) "رجوع" else "Back",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Title Card
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isArabic) "من نحن" else "About Us",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Content
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = if (isArabic) """
                            تطبيق قارئ الكتب الذكي هو منصة متكاملة تهدف إلى إثراء المحتوى العربي وتوفير تجربة قراءة فريدة ومتميزة.
                            
                            نسعى من خلال هذا التطبيق إلى دمج أحدث تقنيات الذكاء الاصطناعي مع جماليات التصميم العربي الأصيل، لتقديم تجربة لا تضاهى للقارئ العربي.
                            
                            المميزات:
                            - مكتبة ضخمة من الكتب العربية والعالمية
                            - واجهة مستخدم عصرية ومريحة للعين
                            - أدوات ذكية للمساعدة في القراءة والفهم
                            - دعم كامل للوضع الليلي والقراءة الصوتية
                            
                            فريق العمل:
                            نخبة من المطورين والمصممين الشغوفين باللغة العربية والتقنية.
                        """.trimIndent() else """
                            Smart Book Reader is a complete platform aiming to enrich Arabic content and provide a unique reading experience.
                            
                            We blend the latest AI technologies with elegant design to offer an unmatched experience.
                            
                            Features:
                            - Large library of Arabic and international books
                            - Modern and eye-comfortable UI
                            - Smart tools to assist reading and understanding
                            - Full support for dark mode and audio reading
                            
                            Team:
                            A passionate team of developers and designers.
                        """.trimIndent(),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Start,
                        lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * 1.5
                    )
                }
            }
        }
    }
}

@Composable
fun ContactItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
