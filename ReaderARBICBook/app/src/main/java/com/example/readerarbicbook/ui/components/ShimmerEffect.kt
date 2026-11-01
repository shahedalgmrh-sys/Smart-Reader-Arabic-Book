package com.example.readerarbicbook.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.readerarbicbook.ui.theme.ShimmerBaseColor
import com.example.readerarbicbook.ui.theme.ShimmerHighlightColor

@Composable
fun ShimmerEffect(
    modifier: Modifier = Modifier,
    width: Dp = 200.dp,
    height: Dp = 20.dp,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(4.dp)
) {
    val shimmerColors = listOf(
        ShimmerBaseColor.copy(alpha = 0.6f),
        ShimmerHighlightColor.copy(alpha = 0.8f),
        ShimmerBaseColor.copy(alpha = 0.6f)
    )

    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 1000,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ), label = "shimmer"
    )

    val brush = Brush.linearGradient(
        colors = shimmerColors,
        start = Offset(x = translateAnim - 200f, y = 0f),
        end = Offset(x = translateAnim + 200f, y = 0f)
    )

    Box(
        modifier = modifier
            .width(width)
            .height(height)
            .clip(shape)
            .background(brush)
    )
}

@Composable
fun ShimmerBookCard(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        // Book cover shimmer
        ShimmerEffect(
            width = 120.dp,
            height = 180.dp,
            shape = RoundedCornerShape(12.dp)
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // Title shimmer
        ShimmerEffect(
            width = 150.dp,
            height = 16.dp,
            shape = RoundedCornerShape(4.dp)
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Author shimmer
        ShimmerEffect(
            width = 100.dp,
            height = 12.dp,
            shape = RoundedCornerShape(4.dp)
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Rating shimmer
        Row {
            repeat(5) {
                ShimmerEffect(
                    width = 16.dp,
                    height = 16.dp,
                    shape = RoundedCornerShape(8.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
        }
    }
}

@Composable
fun ShimmerBookList(
    itemCount: Int = 3
) {
    Column {
        repeat(itemCount) {
            ShimmerBookCard()
            if (it < itemCount - 1) {
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
fun ShimmerSearchBar(
    modifier: Modifier = Modifier
) {
    ShimmerEffect(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        width = Dp.Unspecified,
        height = 56.dp,
        shape = RoundedCornerShape(24.dp)
    )
}

@Composable
fun ShimmerAIRecommendation(
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        // Book cover
        ShimmerEffect(
            width = 80.dp,
            height = 120.dp,
            shape = RoundedCornerShape(8.dp)
        )
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column {
            // Title
            ShimmerEffect(
                width = 200.dp,
                height = 16.dp,
                shape = RoundedCornerShape(4.dp)
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Author
            ShimmerEffect(
                width = 150.dp,
                height = 12.dp,
                shape = RoundedCornerShape(4.dp)
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Description
            repeat(3) {
                ShimmerEffect(
                    width = 250.dp - (it * 20).dp,
                    height = 10.dp,
                    shape = RoundedCornerShape(4.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // AI badge
            ShimmerEffect(
                width = 80.dp,
                height = 20.dp,
                shape = RoundedCornerShape(10.dp)
            )
        }
    }
}