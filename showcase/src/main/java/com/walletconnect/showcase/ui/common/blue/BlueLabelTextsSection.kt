package com.walletconnect.showcase.ui.common

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.stream.IntStream.range

@Composable
fun BlueLabelTexts(title: String, values: List<String>, displayEndSpacer: Boolean = false) {
    InnerContent {
        Text(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 13.dp),
            text = title, style = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = themedColor(darkColor = Color(0xFF9ea9a9), lightColor = Color(0xFF788686)))
        )

        //Note (Szymon): Not suited for tablets. Needed a quick implementation.
        val maxCharacters = 35
        var currentRowCharactersCount = 0
        val currentRowItems = mutableListOf<String>()

        for (i in range(0, values.size)) {
            val currentValue = values[i]
            if (currentRowCharactersCount + currentValue.length < maxCharacters) {
                currentRowItems += currentValue
                currentRowCharactersCount += currentValue.length
            } else {
                BlueLabelRow(values = currentRowItems)
                currentRowItems.clear()
                currentRowCharactersCount = 0
                currentRowItems += currentValue
                currentRowCharactersCount += currentValue.length
            }
        }

        if (currentRowCharactersCount != 0) {
            BlueLabelRow(values = currentRowItems)
        }
    }
    if (displayEndSpacer) Spacer(modifier = Modifier.height(5.dp))
}
