package com.devbyeai.macuisine

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.*
import com.devbyeai.macuisine.serial.PowerBoard
import com.devbyeai.macuisine.serial.SerialManager

class GuideActivity : Activity() {

    private val BG = Color.parseColor("#17130F")
    private val PANEL = Color.parseColor("#201A15")
    private val PANEL2 = Color.parseColor("#2A221B")
    private val FG = Color.parseColor("#F4EDE3")
    private val MUTED = Color.parseColor("#B4A797")
    private val ACCENT = Color.parseColor("#E4A04D")
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.getIntExtra("id", -1)
        recipe = RecipeStore.load(this).firstOrNull { it.id == id } ?: run { finish(); return }

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(BG); setPadding(dp(16),dp(16),dp(16),dp(16)) }

        title = TextView(this).apply { setTextColor(FG); textSize = 20f; typeface = Typeface.create("serif", Typeface.BOLD) }
        root.addView(title)
        val img = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            RecipeStore.image(this@GuideActivity, recipe.id)?.let { setImageBitmap(it) }
        }
        root.addView(img, LinearLayout.LayoutParams(MATCH_PARENT, dp(120)).apply { topMargin = dp(10) })

        stepText = TextView(this).apply { setTextColor(FG); textSize = 16f; setPadding(0,dp(14),0,dp(14)) }
        root.addView(stepText)

        mv = TextView(this).apply { setTextColor(ACCENT); textSize = 15f; setBackgroundColor(PANEL); setPadding(dp(12),dp(12),dp(12),dp(12)) }
        root.addView(mv, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        val nav = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0,dp(14),0,dp(14)) }
        prev = Button(this).apply { text = "Précédent"; setTextColor(FG); setBackgroundColor(PANEL2); setOnClickListener { go(idx - 1) } }
        next = Button(this).apply { text = "Suivant"; setTextColor(Color.parseColor("#1A1005")); setBackgroundColor(ACCENT); setOnClickListener { go(idx + 1) } }
        nav.addView(prev, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        nav.addView(View(this), LinearLayout.LayoutParams(dp(10), dp(1)))
        nav.addView(next, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        root.addView(nav, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        val sh = TextView(this).apply { text = "Trame de l'étape → /dev/ttyMT0"; setTextColor(MUTED); textSize = 11f }
        frame = TextView(this).apply { setTextColor(Color.parseColor("#ECC98F")); textSize = 13f; typeface = Typeface.MONOSPACE; setBackgroundColor(Color.parseColor("#120F0B")); setPadding(dp(12),dp(10),dp(12),dp(10)) }
        root.addView(sh); root.addView(frame, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        SerialManager.start()
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
        // Display only: guided cooking does not drive the motor yet (safety).
        SerialManager.allOff()
    }

    private fun fmt(s: Int) = "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
    override fun onDestroy() { super.onDestroy(); SerialManager.allOff() }
}
