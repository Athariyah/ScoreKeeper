package ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ui.components.CloseButton
import ui.components.GlassCard
import ui.components.InfoChip
import ui.components.MinimizeButton
import ui.components.OvalsCanvas
import ui.components.SettingsButton
import ui.components.SportButton
import ui.components.TennisBallIcon
import ui.components.VolleyballIcon
import ui.components.rememberOvalsAnimation
import ui.theme.AppLanguage
import ui.theme.DesignStage
import ui.theme.LocalStage
import ui.theme.ScoreKeeperTheme
import ui.theme.colorsFor
import ui.theme.stringsFor

/**
 * Главный экран. Координаты взяты из Figma (кадр «Главный экран», 1920×1080).
 * Карточка стоит в (460, 163) и имеет размер 1000×800; внутри неё позиции отсчитываются от её угла.
 */
@Composable
fun MenuScreen(
    onVolleyball: () -> Unit,
    onTennis: () -> Unit,
    onSettings: () -> Unit,
    onExit: () -> Unit,
    onMinimize: () -> Unit,
    darkTheme: Boolean,
    language: AppLanguage,
) {
    ScoreKeeperTheme(colors = colorsFor(darkTheme), strings = stringsFor(language)) {
        val colors = ScoreKeeperTheme.colors
        val strings = ScoreKeeperTheme.strings
        DesignStage {
            val stage = LocalStage.current
            val ovals = rememberOvalsAnimation()

            // 1) фон окна: белая подложка + плывущие овалы
            Box(Modifier.fillMaxSize().background(colors.canvas))
            OvalsCanvas(
                anim = ovals,
                designTopLeft = Offset(-stage.originX.value, -stage.originY.value),
                modifier = Modifier.fillMaxSize(),
            )

            // 2) центральная часть макета (всегда по центру окна)
            Box(Modifier.align(Alignment.Center).requiredSize(1920.dp, 1080.dp)) {
                GlassCard(
                    anim = ovals,
                    designTopLeft = Offset(460f, 163f),
                    modifier = Modifier.offset(460.dp, 163.dp).size(1000.dp, 800.dp),
                ) {
                    LogoTile(Modifier.offset(43.dp, 86.dp))
                    Title(Modifier.offset(293.dp, 146.dp))

                    SportButton(
                        label = strings.volleyball,
                        onClick = onVolleyball,
                        modifier = Modifier.offset(55.dp, 540.dp),
                        iconStart = 35.dp,
                        labelCenterX = 231.5.dp,
                    ) { m -> VolleyballIcon(m) }

                    SportButton(
                        label = strings.tennis,
                        onClick = onTennis,
                        modifier = Modifier.offset(575.dp, 540.dp),
                        iconStart = 28.dp,
                        labelCenterX = 224.5.dp,
                    ) { m -> TennisBallIcon(m) }
                }
            }

            // 3) элементы, привязанные к краям окна
            Row(
                Modifier.align(Alignment.TopEnd).padding(top = 27.dp, end = 44.dp),
                horizontalArrangement = Arrangement.spacedBy(30.dp),
            ) {
                MinimizeButton(onMinimize)
                SettingsButton(onSettings)
                CloseButton(onExit)
            }
            InfoChip(
                "©   Made by Athariyyah and R_CODER",
                Modifier.align(Alignment.BottomStart).padding(start = 23.dp, bottom = 17.dp),
            )
            InfoChip(
                "version: release-1.0.",
                Modifier.align(Alignment.BottomEnd).padding(end = 34.dp, bottom = 17.dp),
            )
        }
    }
}

@Composable
private fun LogoTile(modifier: Modifier = Modifier) {
    // Логотип приложения — картинка из вашего SVG (resources/logo_app.png, 1000×1000):
    // плитка с тёмно-синим градиентом, цифры «30:45» с белой обводкой и свечением.
    Image(
        painter = painterResource("logo_app.png"),
        contentDescription = null,
        contentScale = ContentScale.FillBounds,
        modifier = modifier.size(250.dp),
    )
}

@Composable
private fun Title(modifier: Modifier = Modifier) {
    val colors = ScoreKeeperTheme.colors
    val type = ScoreKeeperTheme.typography
    val px = LocalDensity.current.density

    Box(modifier.size(672.dp, 130.dp), contentAlignment = Alignment.Center) {
        BasicText(
            text = "ScoreKeeper",
            style = type.display.copy(
                brush = Brush.verticalGradient(
                    0f to colors.titleTop,
                    0.803f to colors.titleBottom,
                    1f to colors.titleBottom,
                ),
                shadow = Shadow(
                    color = colors.titleGlow.copy(alpha = 0.71f),
                    offset = Offset.Zero,
                    blurRadius = 35f * px,
                ),
            ),
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Visible,
        )
    }
}
