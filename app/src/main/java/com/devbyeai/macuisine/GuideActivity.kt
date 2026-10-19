package com.devbyeai.macuisine

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.*
import com.devbyeai.macuisine.serial.PowerBoard
import com.devbyeai.macuisine.serial.SerialManager

class GuideActivity : Activity() {

    private val BG          = Color.parseColor("#EEF1EC")
    private val SURFACE     = Color.parseColor("#FFFFFF")
    private val SURFACE2    = Color.parseColor("#F5F8F3")
    private val INK         = Color.parseColor("#18231D")
    private val MUTED       = Color.parseColor("#67736C")
    private val FAINT       = Color.parseColor("#9AA69E")
    private val ACCENT      = Color.parseColor("#2F9E5B")
    private val ACCENT_SOFT = Color.parseColor("#E3F3EA")
    private val ACCENT_DEEP = Color.parseColor("#1F7A44")
    private val LINE        = Color.parseColor("#E3E9E2")
    private val TEMPC = intArrayOf(0,37,45,50,55,60,65,70,75,80,85,90,95,100,105,110,115,120,130)

    private lateinit var recipe: Recipe
    private var idx = 0

    private lateinit var title: TextView
    private lateinit var stepText: TextView
    private lateinit var mv: TextView
    private lateinit var frame: TextView
    private lateinit var prev: Button
    private lateinit var next: Button

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun round(color: Int, r: Int = 18, stroke: Int = 0, strokeColor: Int = LINE): GradientDrawable =
        GradientDrawable().apply {
            setColor(color); cornerRadius = dp(r).toFloat()
            if (stroke > 0) setStroke(dp(stroke), strokeColor)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.getIntExtra("id", -1)
        recipe = RecipeStore.load(this).firstOrNull { it.id == id } ?: run { finish(); return }

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(BG); setPadding(dp(16),dp(18),dp(16),dp(18)) }

        title = TextView(this).apply { setTextColor(INK); textSize = 22f; typeface = Typeface.create("serif", Typeface.BOLD) }
        root.addView(title)
        val imgWrap = LinearLayout(this).apply { background = round(SURFACE, 18); clipToOutline = true }
        val img = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            RecipeStore.image(this@GuideActivity, recipe.id)?.let { setImageBitmap(it) } ?: setBackgroundColor(ACCENT_SOFT)
        }
        imgWrap.addView(img, LinearLayout.LayoutParams(MATCH_PARENT, dp(150)))
        root.addView(imgWrap, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(12) })

        stepText = TextView(this).apply { setTextColor(INK); textSize = 16f; setPadding(0,dp(16),0,dp(14)) }
        root.addView(stepText)

        mv = TextView(this).apply { setTextColor(ACCENT_DEEP); textSize = 15f; typeface = Typeface.DEFAULT_BOLD; background = round(ACCENT_SOFT, 16); setPadding(dp(14),dp(14),dp(14),dp(14)) }
        root.addView(mv, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        val nav = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0,dp(16),0,dp(14)) }
        prev = Button(this).apply { text = "Précédent"; setTextColor(MUTED); typeface = Typeface.DEFAULT_BOLD; background = round(SURFACE, 24, 1, LINE); stateListAnimator = null; setOnClickListener { go(idx - 1) } }
        next = Button(this).apply { text = "Suivant"; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD; background = round(ACCENT, 24); stateListAnimator = null; setOnClickListener { go(idx + 1) } }
        nav.addView(prev, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        nav.addView(View(this), LinearLayout.LayoutParams(dp(12), dp(1)))
        nav.addView(next, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        root.addView(nav, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        val serialBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; background = round(SURFACE2, 14); setPadding(dp(14),dp(10),dp(14),dp(10)) }
        val sh = TextView(this).apply { text = "Trame de l'étape"; setTextColor(FAINT); textSize = 11f }
        frame = TextView(this).apply { setTextColor(MUTED); textSize = 12f; typeface = Typeface.MONOSPACE }
        serialBox.addView(sh); serialBox.addView(frame)
        root.addView(serialBox, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        try { SerialManager.start(this) } catch (_: Exception) {}
        setContentView(ScrollView(this).apply { setBackgroundColor(BG); addView(root) })
        go(0)
    }

    private fun go(i: Int) {
        if (recipe.steps.isEmpty()) { finish(); return }
        idx = i.coerceIn(0, recipe.steps.size - 1)
        val s = recipe.steps[idx]
        title.text = "${recipe.name} · étape ${idx + 1}/${recipe.steps.size}"
        stepText.text = s.text
        val tC = if (s.temp > 0) TEMPC[s.temp.coerceAtMost(18)] else 0
        mv.text = "Temps ${if (s.time>0) fmt(s.time) else "—"}    " +
                  "Vitesse ${if (s.speed>0) (if (s.reverse) "↺" else "") + s.speed else "—"}    " +
                  "Température ${if (s.temp>0) "$tC°" else "—"}"
        prev.isEnabled = idx > 0
        next.text = if (idx < recipe.steps.size - 1) "Suivant" else "Terminé"
        val cmd = PowerBoard.autoCmd(s.speed, s.temp, false)
        val f = PowerBoard.frame(cmd, s.speed, s.temp, s.reverse)
        frame.text = PowerBoard.hex(f)
        // Display only: guided cooking does not drive the motor (safety).
        SerialManager.allOff()
    }

    private fun fmt(s: Int) = "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
    override fun onDestroy() { super.onDestroy(); SerialManager.allOff() }
}
