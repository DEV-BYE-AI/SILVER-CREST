package com.devbyeai.macuisine

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.*
import com.devbyeai.macuisine.serial.PowerBoard
import com.devbyeai.macuisine.serial.SerialManager

class MainActivity : Activity() {

    // Light modern palette (from the validated design)
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
    private val HOT         = Color.parseColor("#D9623A")

    private val TEMPC = intArrayOf(0,37,45,50,55,60,65,70,75,80,85,90,95,100,105,110,115,120,130)
    private val TIME_STEPS = intArrayOf(0,5,10,15,20,30,45,60,90,120,180,300,600,900,1800,2700,3600)

    private var speed = 0
    private var tempLvl = 0
    private var timeS = 0
    private var reverse = false
    private var turbo = false
    private var running = false

    private lateinit var tTime: TextView
    private lateinit var tSpeed: TextView
    private lateinit var tTemp: TextView
    private lateinit var tStatus: TextView
    private lateinit var tFrame: TextView
    private lateinit var goBtn: Button
    private lateinit var tabCookBtn: Button
    private lateinit var tabRecBtn: Button
    private lateinit var cookView: View
    private lateinit var recipesView: View
    private var showingCook = true

    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            if (running) {
                if (timeS > 0) timeS-- else running = false
                renderCook()
                sendCurrent()
            }
            handler.postDelayed(this, 1000)
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun round(color: Int, r: Int = 20, stroke: Int = 0, strokeColor: Int = LINE): GradientDrawable =
        GradientDrawable().apply {
            setColor(color); cornerRadius = dp(r).toFloat()
            if (stroke > 0) setStroke(dp(stroke), strokeColor)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try { com.devbyeai.macuisine.serial.StockProbe.restore(this) } catch (_: Exception) {}
        try { SerialManager.start(this) } catch (_: Exception) {}

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BG)
            setPadding(dp(16), dp(18), dp(16), dp(18))
        }

        // top bar
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val brand = TextView(this).apply {
            text = "Ma Cuisine"; setTextColor(INK); textSize = 26f
            typeface = Typeface.create("serif", Typeface.BOLD)
        }
        tabCookBtn = tab("Cuisiner") { showCook(true) }
        tabRecBtn = tab("Recettes") { showCook(false) }
        top.addView(brand, lp(0, WRAP_CONTENT, 1f))
        top.addView(tabCookBtn); top.addView(space(8)); top.addView(tabRecBtn)
        root.addView(top, lp(MATCH_PARENT, WRAP_CONTENT))
        root.addView(space(16))

        cookView = buildCook()
        recipesView = buildRecipes()
        root.addView(cookView, lp(MATCH_PARENT, WRAP_CONTENT))
        root.addView(recipesView, lp(MATCH_PARENT, MATCH_PARENT))

        setContentView(ScrollView(this).apply { setBackgroundColor(BG); addView(root) })
        showCook(true)
        renderCook()
        handler.postDelayed(ticker, 1000)
    }

    private fun buildCook(): View {
        val v = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = round(SURFACE, 22); setPadding(dp(20),dp(22),dp(20),dp(22))
        }
        val dials = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        tTime = bigNum("0:00")
        tSpeed = bigNum("0")
        tTemp = bigNum("Off")
        dials.addView(dial("Temps", tTime, { stepTime(-1) }, { stepTime(1) }), lp(0, WRAP_CONTENT, 1f))
        dials.addView(dial("Vitesse", tSpeed, { speed = (speed-1).coerceAtLeast(0); turbo=false; afterChange() }, { speed = (speed+1).coerceAtMost(10); turbo=false; afterChange() }), lp(0, WRAP_CONTENT, 1f))
        dials.addView(dial("Temp. °C", tTemp, { tempLvl = (tempLvl-1).coerceAtLeast(0); afterChange() }, { tempLvl = (tempLvl+1).coerceAtMost(18); afterChange() }), lp(0, WRAP_CONTENT, 1f))
        v.addView(dials, lp(MATCH_PARENT, WRAP_CONTENT))
        v.addView(space(20))

        val ctr = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        val turboBtn = pill("Turbo") { turbo = !turbo; afterChange() }
        val revBtn = pill("Sens inv.") { reverse = !reverse; afterChange() }
        goBtn = Button(this).apply {
            text = "Départ"; setTextColor(Color.WHITE); textSize = 15f
            typeface = Typeface.DEFAULT_BOLD; background = round(ACCENT, 28); stateListAnimator = null
            setOnClickListener {
                running = !running
                try {
                    if (running) SerialManager.takeControl(this@MainActivity)
                    else SerialManager.releaseControl()
                } catch (_: Exception) {}
                renderCook(); sendCurrent()
            }
        }
        val lidlBtn = pill("Ouvrir Lidl") {
            try { SerialManager.releaseControl() } catch (_: Exception) {}
            running = false
            try { com.devbyeai.macuisine.serial.StockProbe.restore(this) } catch (_: Exception) {}
            try {
                packageManager.getLaunchIntentForPackage("de.silpion.mc2")?.let { startActivity(it) }
                    ?: Toast.makeText(this, "Appli Lidl introuvable", Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {
                Toast.makeText(this, "Impossible d'ouvrir l'appli Lidl", Toast.LENGTH_SHORT).show()
            }
        }
        ctr.addView(turboBtn); ctr.addView(space(8)); ctr.addView(revBtn); ctr.addView(space(8))
        ctr.addView(goBtn, lp(dp(104), dp(56))); ctr.addView(space(8)); ctr.addView(lidlBtn)
        v.addView(ctr, lp(MATCH_PARENT, WRAP_CONTENT))
        v.addView(space(16))

        tStatus = TextView(this).apply { setTextColor(MUTED); textSize = 13f; gravity = Gravity.CENTER }
        v.addView(tStatus, lp(MATCH_PARENT, WRAP_CONTENT))
        v.addView(space(12))

        val serialBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; background = round(SURFACE2, 14); setPadding(dp(14),dp(10),dp(14),dp(10))
        }
        val sh = TextView(this).apply { text = "Liaison carte · ${SerialManager.device} @ ${SerialManager.BAUD}"; setTextColor(FAINT); textSize = 11f }
        tFrame = TextView(this).apply { setTextColor(MUTED); textSize = 12f; typeface = Typeface.MONOSPACE }
        serialBox.addView(sh); serialBox.addView(space(4)); serialBox.addView(tFrame)
        v.addView(serialBox, lp(MATCH_PARENT, WRAP_CONTENT))
        return v
    }

    private fun buildRecipes(): View {
        val scroll = ScrollView(this)
        val grid = GridLayout(this).apply { columnCount = 2; setPadding(0, 0, 0, dp(20)) }
        val recipes = RecipeStore.load(this)
        val shown = recipes.take(48)
        for (r in shown) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = round(SURFACE, 18); clipToOutline = true
                setPadding(0,0,0,dp(12)); setOnClickListener {
                    startActivity(Intent(this@MainActivity, GuideActivity::class.java).putExtra("id", r.id))
                }
            }
            val img = ImageView(this).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                RecipeStore.image(this@MainActivity, r.id)?.let { setImageBitmap(it) } ?: setBackgroundColor(ACCENT_SOFT)
            }
            card.addView(img, LinearLayout.LayoutParams(MATCH_PARENT, dp(120)))
            val nm = TextView(this).apply { text = r.name; setTextColor(INK); textSize = 15f; setPadding(dp(12),dp(10),dp(12),dp(2)); typeface = Typeface.create("serif", Typeface.BOLD) }
            val meta = TextView(this).apply { text = "${r.duration} min · ${r.level}"; setTextColor(MUTED); textSize = 11f; setPadding(dp(12),0,dp(12),0) }
            card.addView(nm); card.addView(meta)
            val glp = GridLayout.LayoutParams().apply {
                width = 0; height = WRAP_CONTENT
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(dp(6), dp(6), dp(6), dp(6))
            }
            grid.addView(card, glp)
        }
        scroll.addView(grid)
        return scroll
    }

    private fun showCook(cook: Boolean) {
        showingCook = cook
        cookView.visibility = if (cook) View.VISIBLE else View.GONE
        recipesView.visibility = if (cook) View.GONE else View.VISIBLE
        styleTab(tabCookBtn, cook)
        styleTab(tabRecBtn, !cook)
    }

    private fun styleTab(b: Button, active: Boolean) {
        if (active) { b.background = round(ACCENT, 24); b.setTextColor(Color.WHITE) }
        else { b.background = round(SURFACE, 24, 1, LINE); b.setTextColor(MUTED) }
    }

    private fun afterChange() { renderCook(); if (running) sendCurrent() }

    private fun stepTime(d: Int) {
        var i = TIME_STEPS.indexOf(timeS); if (i < 0) i = 0
        i = (i + d).coerceIn(0, TIME_STEPS.size - 1); timeS = TIME_STEPS[i]; afterChange()
    }

    private fun renderCook() {
        tTime.text = fmt(timeS)
        tSpeed.text = if (turbo) "Turbo" else speed.toString()
        tTemp.text = if (tempLvl == 0) "Off" else TEMPC[tempLvl].toString()
        goBtn.text = if (running) "Stop" else "Départ"
        goBtn.background = round(if (running) HOT else ACCENT, 28)
        val mode = if (SerialManager.simulated) "affichage seul" else "connecté"
        tStatus.text = (if (running) "En marche" else "Au repos") + " · $mode"
        tFrame.text = PowerBoard.hex(currentFrame())
    }

    private fun currentFrame(): ByteArray {
        val sp = if (turbo) 10 else speed
        val cmd = PowerBoard.autoCmd(sp, tempLvl, turbo)
        return PowerBoard.frame(cmd, sp, tempLvl, reverse, turbo)
    }

    private fun sendCurrent() {
        val f = if (running) currentFrame() else PowerBoard.OFF
        SerialManager.setFrame(f)
    }

    private fun fmt(s: Int): String = "${s / 60}:${(s % 60).toString().padStart(2, '0')}"

    // --- view helpers ---
    private fun lp(w: Int, h: Int, weight: Float = 0f) = LinearLayout.LayoutParams(w, h, weight)
    private fun space(px: Int) = View(this).apply { layoutParams = LinearLayout.LayoutParams(dp(px), dp(px)) }
    private fun bigNum(t: String) = TextView(this).apply { text = t; setTextColor(INK); textSize = 36f; gravity = Gravity.CENTER; typeface = Typeface.create("serif", Typeface.BOLD) }

    private fun tab(label: String, onClick: () -> Unit): Button = Button(this).apply {
        text = label; textSize = 13f; typeface = Typeface.DEFAULT_BOLD
        background = round(SURFACE, 24, 1, LINE); setTextColor(MUTED); stateListAnimator = null
        setPadding(dp(16), 0, dp(16), 0)
        setOnClickListener { onClick() }
    }
    private fun pill(label: String, onClick: () -> Unit): Button = Button(this).apply {
        text = label; textSize = 12f; typeface = Typeface.DEFAULT_BOLD
        background = round(ACCENT_SOFT, 22); setTextColor(ACCENT_DEEP); stateListAnimator = null
        setOnClickListener { onClick() }
    }
    private fun dial(cap: String, numView: TextView, minus: () -> Unit, plus: () -> Unit): View {
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
        col.addView(numView, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        val capV = TextView(this).apply { text = cap; setTextColor(MUTED); textSize = 12f; gravity = Gravity.CENTER }
        col.addView(capV, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        row.addView(stepBtn("–", minus)); row.addView(space(8)); row.addView(stepBtn("+", plus))
        col.addView(space(10)); col.addView(row, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT))
        return col
    }
    private fun stepBtn(label: String, onClick: () -> Unit): Button = Button(this).apply {
        text = label; textSize = 18f; setTextColor(ACCENT_DEEP); typeface = Typeface.DEFAULT_BOLD
        background = round(SURFACE2, 16, 1, LINE); stateListAnimator = null
        minimumWidth = dp(46); setPadding(0,0,0,0)
        setOnClickListener { onClick() }
    }

    override fun onDestroy() { super.onDestroy(); handler.removeCallbacks(ticker); SerialManager.stop() }
}
